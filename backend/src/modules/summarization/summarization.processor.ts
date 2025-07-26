import { Processor, WorkerHost, OnWorkerEvent } from '@nestjs/bullmq';
import { Logger } from '@nestjs/common';
import { Job } from 'bullmq';
import { db } from '@/database/connection';
import { messages, summaries, NewSummary } from '@/database/schema';
import { eq, and, gte, lte } from 'drizzle-orm';
import { OpenAIService, SummarizationRequest } from './openai.service';
import { TopicFilterService } from './topic-filter.service';
import { environment } from '@/environments/environment';
import { randomUUID } from 'crypto';

export interface SummarizationJobData {
  chatId: string;
  windowStart: string;
  windowEnd: string;
}

@Processor('summarization')
export class SummarizationProcessor extends WorkerHost {
  private readonly logger = new Logger(SummarizationProcessor.name);

  constructor(
    private readonly openaiService: OpenAIService,
    private readonly topicFilterService: TopicFilterService,
  ) {
    super();
  }

  async process(job: Job<SummarizationJobData>): Promise<void> {
    const { chatId, windowStart, windowEnd } = job.data;
    this.logger.log(`Processing summarization job: ${job.id} for chat ${chatId}`);

    try {
      const periodStart = new Date(windowStart);
      const periodEnd = new Date(windowEnd);

      // Fetch messages in the time window
      const chatMessages = await this.fetchMessagesInWindow(chatId, periodStart, periodEnd);

      if (chatMessages.length < environment.summarization.minMessagesForSummary) {
        this.logger.log(
          `Skipping summarization for chat ${chatId}: only ${chatMessages.length} messages (min: ${environment.summarization.minMessagesForSummary})`
        );
        return;
      }

      // Filter out blocked topics
      const filteredMessages = await this.filterBlockedTopics(chatMessages);

      if (filteredMessages.length === 0) {
        this.logger.log(`All messages filtered out for chat ${chatId} - likely spam/blocked topics`);
        return;
      }

      // Check if summary already exists for this period
      const existingSummary = await this.checkExistingSummary(chatId, periodStart, periodEnd);
      if (existingSummary) {
        this.logger.log(`Summary already exists for chat ${chatId} in period ${windowStart} - ${windowEnd}`);
        return;
      }

      // Prepare summarization request
      const request: SummarizationRequest = {
        chatId,
        messages: filteredMessages.map(msg => ({
          sender: msg.sender,
          body: msg.body,
          timestamp: msg.tsOriginal,
        })),
        periodStart,
        periodEnd,
      };

      // Generate summary using OpenAI
      const result = await this.openaiService.summarizeMessages(request);

      // Save summary to database
      const summaryId = randomUUID();
      const newSummary: NewSummary = {
        id: summaryId,
        chatId,
        summaryText: result.summary,
        periodStart,
        periodEnd,
        messageCount: filteredMessages.length,
        model: result.model,
        tokensUsed: result.tokensUsed,
        createdAt: new Date(),
        metadata: JSON.stringify({
          originalMessageCount: chatMessages.length,
          filteredMessageCount: filteredMessages.length,
          jobId: job.id,
          processingTime: Date.now(),
        }),
      };

      await db.insert(summaries).values(newSummary);

      // Mark messages as processed
      await this.markMessagesAsProcessed(chatMessages.map(m => m.id));

      this.logger.log(
        `Summary created for chat ${chatId}: ${summaryId} (${filteredMessages.length}/${chatMessages.length} messages, ${result.tokensUsed} tokens)`
      );

    } catch (error) {
      this.logger.error(`Failed to process summarization job ${job.id}:`, error);
      throw error; // Re-throw to trigger retry mechanism
    }
  }

  private async fetchMessagesInWindow(
    chatId: string,
    periodStart: Date,
    periodEnd: Date,
  ) {
    return await db
      .select()
      .from(messages)
      .where(
        and(
          eq(messages.chatId, chatId),
          gte(messages.tsOriginal, periodStart),
          lte(messages.tsOriginal, periodEnd),
          eq(messages.isProcessed, false)
        )
      )
      .orderBy(messages.tsOriginal);
  }

  private async filterBlockedTopics(messageList: any[]) {
    const filteredMessages = [];

    for (const message of messageList) {
      try {
        const isBlocked = await this.topicFilterService.isBlockedTopic(message.body);
        if (!isBlocked) {
          filteredMessages.push(message);
        } else {
          this.logger.debug(`Message blocked by topic filter: ${message.id}`);
        }
      } catch (error) {
        this.logger.warn(`Topic filtering failed for message ${message.id}, including in summary:`, error);
        filteredMessages.push(message); // Include on filter failure
      }
    }

    return filteredMessages;
  }

  private async checkExistingSummary(
    chatId: string,
    periodStart: Date,
    periodEnd: Date,
  ) {
    const existing = await db
      .select()
      .from(summaries)
      .where(
        and(
          eq(summaries.chatId, chatId),
          eq(summaries.periodStart, periodStart),
          eq(summaries.periodEnd, periodEnd)
        )
      )
      .limit(1);

    return existing.length > 0 ? existing[0] : null;
  }

  private async markMessagesAsProcessed(messageIds: string[]) {
    if (messageIds.length === 0) return;

    await db
      .update(messages)
      .set({
        isProcessed: true,
        processedAt: new Date(),
      })
      .where(
        and(
          ...messageIds.map(id => eq(messages.id, id))
        )
      );
  }

  @OnWorkerEvent('completed')
  onCompleted(job: Job) {
    this.logger.log(`Summarization job completed: ${job.id}`);
  }

  @OnWorkerEvent('failed')
  onFailed(job: Job, err: Error) {
    this.logger.error(`Summarization job failed: ${job.id}`, err);
  }

  @OnWorkerEvent('progress')
  onProgress(job: Job, progress: number) {
    this.logger.debug(`Summarization job progress: ${job.id} - ${progress}%`);
  }
}
