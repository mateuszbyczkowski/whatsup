import {Injectable, Logger, NotFoundException} from '@nestjs/common';
import {Queue} from 'bullmq';
import {InjectQueue} from '@nestjs/bullmq';
import {db} from '@/database/connection';
import {messages, summaries} from '@/database/schema';
import {and, count, desc, eq, gte, lte, sql} from 'drizzle-orm';
import {
  ChatListDto,
  GetChatsResponseDto,
  GetSummariesResponseDto,
  GetSummaryMarkdownResponseDto,
  GetSummaryQueryDto,
  SummaryDto,
} from './dto/summary.dto';

@Injectable()
export class SummariesService {
  private readonly logger = new Logger(SummariesService.name);

  constructor(
    @InjectQueue('summarization') private readonly summarizationQueue: Queue,
  ) {}

  async getSummariesForChat(
    chatId: string,
    deviceId: string,
    query: GetSummaryQueryDto,
  ): Promise<GetSummariesResponseDto> {
    this.logger.log(`Getting summaries for chat ${chatId}, device ${deviceId}`);

    const limit = query.limit || 50;
    const offset = query.offset || 0;

    try {
      // Build where conditions
      const whereConditions = [eq(summaries.chatId, chatId)];

      if (query.from) {
        whereConditions.push(gte(summaries.periodStart, new Date(query.from)));
      }

      if (query.to) {
        whereConditions.push(lte(summaries.periodEnd, new Date(query.to)));
      }

      // Get total count for pagination
      const [totalResult] = await db
        .select({ count: count() })
        .from(summaries)
        .where(and(...whereConditions));

      const total = totalResult.count;

      // Get summaries with pagination
      const summaryResults = await db
        .select()
        .from(summaries)
        .where(and(...whereConditions))
        .orderBy(desc(summaries.periodStart))
        .limit(limit)
        .offset(offset);

      // Convert to DTOs
      const summaryDtos: SummaryDto[] = summaryResults.map(summary => ({
        id: summary.id,
        chatId: summary.chatId,
        summaryText: summary.summaryText,
        periodStart: summary.periodStart.toISOString(),
        periodEnd: summary.periodEnd.toISOString(),
        messageCount: summary.messageCount,
        model: summary.model,
        tokensUsed: summary.tokensUsed || undefined,
        createdAt: summary.createdAt.toISOString(),
        metadata: summary.metadata || undefined,
      }));

      const response: GetSummariesResponseDto = {
        summaries: summaryDtos,
        chatId,
        total,
        count: summaryDtos.length,
        query: {
          from: query.from,
          to: query.to,
          limit,
          offset,
        },
        timestamp: Date.now(),
      };

      this.logger.log(`Retrieved ${summaryDtos.length} summaries for chat ${chatId}`);
      return response;

    } catch (error) {
      this.logger.error(`Failed to get summaries for chat ${chatId}:`, error);
      throw error;
    }
  }

  async getSummariesAsMarkdown(
    chatId: string,
    deviceId: string,
    query: GetSummaryQueryDto,
  ): Promise<GetSummaryMarkdownResponseDto> {
    this.logger.log(`Getting markdown digest for chat ${chatId}`);

    const summariesResponse = await this.getSummariesForChat(chatId, deviceId, {
      ...query,
      limit: 1000, // Get more for comprehensive digest
    });

    if (summariesResponse.summaries.length === 0) {
      throw new NotFoundException(`No summaries found for chat ${chatId}`);
    }

    const markdown = this.formatSummariesAsMarkdown(chatId, summariesResponse.summaries);

    // Calculate stats
    const totalMessages = summariesResponse.summaries.reduce(
      (sum, summary) => sum + summary.messageCount,
      0
    );

    const oldestSummary = summariesResponse.summaries[summariesResponse.summaries.length - 1];
    const newestSummary = summariesResponse.summaries[0];

    const periodStart = query.from || oldestSummary.periodStart;
    const periodEnd = query.to || newestSummary.periodEnd;
    const timeSpan = this.calculateTimeSpan(new Date(periodStart), new Date(periodEnd));

    return {
      markdown,
      chatId,
      period: {
        from: periodStart,
        to: periodEnd,
      },
      stats: {
        summaryCount: summariesResponse.summaries.length,
        totalMessages,
        timeSpan,
      },
      timestamp: Date.now(),
    };
  }

  async getAvailableChats(deviceId: string): Promise<GetChatsResponseDto> {
    this.logger.log(`Getting all chats with messages for device ${deviceId}`);

    try {
      // Get all chats with messages for the device
      const chatMessageCounts = await db
        .select({
          chatId: messages.chatId,
          totalMessages: count(messages.id),
        })
        .from(messages)
        .where(eq(messages.deviceId, deviceId))
        .groupBy(messages.chatId);


      this.logger.log(`Raw chatMessageCounts for device ${deviceId}: ${JSON.stringify(chatMessageCounts)}`);
      if (!chatMessageCounts || chatMessageCounts.length === 0) {
        this.logger.warn(`No chats with messages found for device ${deviceId}`);
      }

      // For each chat, check if there is at least one summary
      const chatSummaries = await db
        .select({
          chatId: summaries.chatId,
          summaryCount: count(summaries.id),
          lastSummaryAt: sql<Date>`MAX(${summaries.createdAt})`,
          firstSummaryAt: sql<Date>`MIN(${summaries.createdAt})`,
        })
        .from(summaries)
        .where(
          sql`${summaries.chatId} IN (
            SELECT DISTINCT ${messages.chatId}
            FROM ${messages}
            WHERE ${messages.deviceId} = ${deviceId}
          )`
        )
        .groupBy(summaries.chatId);


      this.logger.log(`Raw chatSummaries for device ${deviceId}: ${JSON.stringify(chatSummaries)}`);

      // Combine the data
      const chats: ChatListDto[] = chatMessageCounts.map(mc => {
        const summary = chatSummaries.find(cs => cs.chatId === mc.chatId);
        return {
          chatId: mc.chatId,
          totalMessages: mc.totalMessages,
          summaryCount: summary ? summary.summaryCount : 0,
          lastSummaryAt: summary && summary.lastSummaryAt ? summary.lastSummaryAt.toISOString() : undefined,
          firstSummaryAt: summary && summary.firstSummaryAt ? summary.firstSummaryAt.toISOString() : undefined,
        };
      });

      const response: GetChatsResponseDto = {
        chats,
        total: chats.length,
        timestamp: Date.now(),
      };

      this.logger.log(`Found ${chats.length} chats with messages for device ${deviceId}`);
      if (chats.length === 0) {
        this.logger.warn(`No chats returned in response for device ${deviceId}`);
      }
      return response;

    } catch (error) {
      this.logger.error(`Failed to get available chats for device ${deviceId}:`, error);
      throw error;
    }
  }

  private formatSummariesAsMarkdown(chatId: string, summaries: SummaryDto[]): string {
    const chatName = this.formatChatName(chatId);

    let markdown = `# WhatsApp Digest for ${chatName}\n\n`;
    markdown += `*Generated on ${new Date().toLocaleString()}*\n\n`;

    if (summaries.length === 0) {
      markdown += '## No summaries available\n\n';
      return markdown;
    }

    // Group summaries by date
    const summariesByDate = this.groupSummariesByDate(summaries);

    for (const [date, dateSummaries] of Object.entries(summariesByDate)) {
      markdown += `## ${date}\n\n`;

      for (const summary of dateSummaries) {
        const startTime = new Date(summary.periodStart).toLocaleTimeString('en-US', {
          hour: '2-digit',
          minute: '2-digit',
        });
        const endTime = new Date(summary.periodEnd).toLocaleTimeString('en-US', {
          hour: '2-digit',
          minute: '2-digit',
        });

        markdown += `### ${startTime} - ${endTime} (${summary.messageCount} messages)\n\n`;
        markdown += `${summary.summaryText}\n\n`;
        markdown += `*Generated with ${summary.model}`;

        if (summary.tokensUsed) {
          markdown += ` • ${summary.tokensUsed} tokens`;
        }

        markdown += `*\n\n---\n\n`;
      }
    }

    return markdown;
  }

  private formatChatName(chatId: string): string {
    // Convert chat IDs like "group_family_chat" to "Family Chat"
    return chatId
      .replace(/^(group_|private_)/, '')
      .split('_')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }

  private groupSummariesByDate(summaries: SummaryDto[]): Record<string, SummaryDto[]> {
    const grouped: Record<string, SummaryDto[]> = {};

    for (const summary of summaries) {
      const date = new Date(summary.periodStart).toLocaleDateString('en-US', {
        weekday: 'long',
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      });

      if (!grouped[date]) {
        grouped[date] = [];
      }

      grouped[date].push(summary);
    }

    // Sort each day's summaries by time
    for (const date in grouped) {
      grouped[date].sort((a, b) =>
        new Date(a.periodStart).getTime() - new Date(b.periodStart).getTime()
      );
    }

    return grouped;
  }

  private calculateTimeSpan(start: Date, end: Date): string {
    const diffMs = end.getTime() - start.getTime();
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
    const diffHours = Math.floor((diffMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));

    if (diffDays > 0) {
      return `${diffDays} day${diffDays === 1 ? '' : 's'}`;
    } else if (diffHours > 0) {
      return `${diffHours} hour${diffHours === 1 ? '' : 's'}`;
    } else {
      return 'Less than 1 hour';
    }
  }

  async getSummaryStats(deviceId: string): Promise<any> {
    try {
      // Get overall stats for the device
      const [messageStats] = await db
        .select({
          totalMessages: count(messages.id),
          totalChats: sql<number>`COUNT(DISTINCT ${messages.chatId})`,
        })
        .from(messages)
        .where(eq(messages.deviceId, deviceId));

      const [summaryStats] = await db
        .select({
          totalSummaries: count(summaries.id),
          totalTokensUsed: sql<number>`SUM(COALESCE(${summaries.tokensUsed}, 0))`,
        })
        .from(summaries)
        .where(
          sql`${summaries.chatId} IN (
            SELECT DISTINCT ${messages.chatId}
            FROM ${messages}
            WHERE ${messages.deviceId} = ${deviceId}
          )`
        );

      return {
        deviceId,
        totalMessages: messageStats.totalMessages,
        totalChats: messageStats.totalChats,
        totalSummaries: summaryStats.totalSummaries,
        totalTokensUsed: summaryStats.totalTokensUsed,
        timestamp: Date.now(),
      };

    } catch (error) {
      this.logger.error(`Failed to get summary stats for device ${deviceId}:`, error);
      throw error;
    }
  }

  /**
   * Manually trigger summarization for a device and optionally a specific chat.
   * This is a stub; implement your summarization logic here.
   */
  async triggerSummarization(deviceId: string, chatId?: string): Promise<void> {
    this.logger.log(`Manual summarization trigger for device ${deviceId}${chatId ? ', chat ' + chatId : ''}`);

    // Get all chats with unprocessed messages for the device
    let chats: { chatId: string }[];
    if (chatId) {
      chats = [{ chatId }];
    } else {
      chats = await db
        .select({ chatId: messages.chatId })
        .from(messages)
        .where(and(eq(messages.deviceId, deviceId), eq(messages.isProcessed, false)))
        .groupBy(messages.chatId);
    }

    for (const chat of chats) {
      // Find the earliest and latest unprocessed message timestamps for the chat
      const [window] = await db
        .select({
          windowStart: sql`MIN(${messages.tsOriginal})`,
          windowEnd: sql`MAX(${messages.tsOriginal})`,
        })
        .from(messages)
        .where(and(eq(messages.deviceId, deviceId), eq(messages.chatId, chat.chatId), eq(messages.isProcessed, false)));

      if (!window || !window.windowStart || !window.windowEnd) {
        this.logger.log(`No unprocessed messages for chat ${chat.chatId}`);
        continue;
      }

      await this.summarizationQueue.add('summarize', {
        chatId: chat.chatId,
        windowStart: window.windowStart,
        windowEnd: window.windowEnd,
      });
      this.logger.log(`Enqueued summarization job for chat ${chat.chatId} (${window.windowStart} - ${window.windowEnd})`);
    }
  }
}
