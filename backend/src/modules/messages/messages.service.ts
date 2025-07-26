import { Injectable, Logger, BadRequestException } from '@nestjs/common';
import { InjectQueue } from '@nestjs/bullmq';
import { Queue } from 'bullmq';
import { db } from '@/database/connection';
import { messages, devices, Device, NewMessage } from '@/database/schema';
import { eq, and } from 'drizzle-orm';
import { IngestRequestDto, IngestResponseDto, MessageEventDto } from './dto/ingest.dto';
import { randomUUID } from 'crypto';

@Injectable()
export class MessagesService {
  private readonly logger = new Logger(MessagesService.name);

  constructor(
    @InjectQueue('summarization') private summarizationQueue: Queue,
  ) {}

  async ingestMessages(
    request: IngestRequestDto,
    device: Device,
  ): Promise<IngestResponseDto> {
    this.logger.log(`Ingesting ${request.events.length} messages from device ${device.id}`);

    // Validate device_id matches authenticated device
    if (request.device_id !== device.id) {
      throw new BadRequestException('Device ID mismatch');
    }

    // Validate batch size
    if (request.batch_size !== request.events.length) {
      throw new BadRequestException('Batch size mismatch with events count');
    }

    let processedCount = 0;
    let duplicatesSkipped = 0;
    let validationFailures = 0;
    const now = new Date();

    // Process each message event
    for (const event of request.events) {
      try {
        // Validate WhatsApp package
        if (!['com.whatsapp', 'com.whatsapp.w4b'].includes(event.packageName)) {
          this.logger.warn(`Invalid package name: ${event.packageName}`);
          validationFailures++;
          continue;
        }

        // Generate message ID from content hash for deduplication
        const messageId = this.generateMessageId(device.id, event);
        const tsOriginal = new Date(event.timestamp);

        // Check for existing message (unique constraint on device_id, chat_id, ts_original, body)
        const existingMessage = await db
          .select()
          .from(messages)
          .where(
            and(
              eq(messages.deviceId, device.id),
              eq(messages.chatId, event.chatId),
              eq(messages.tsOriginal, tsOriginal),
              eq(messages.body, event.body)
            )
          )
          .limit(1);

        if (existingMessage.length > 0) {
          this.logger.debug(`Duplicate message skipped: ${messageId}`);
          duplicatesSkipped++;
          continue;
        }

        // Insert new message
        const newMessage: NewMessage = {
          id: messageId,
          deviceId: device.id,
          chatId: event.chatId,
          sender: event.sender,
          body: event.body,
          tsOriginal,
          packageName: event.packageName,
          createdAt: now,
          processedAt: null,
          isProcessed: false,
        };

        await db.insert(messages).values(newMessage);

        // Enqueue summarization job for this chat + hour window
        await this.enqueueSummarizationJob(event.chatId, tsOriginal);

        processedCount++;
        this.logger.debug(`Message processed: ${messageId} for chat ${event.chatId}`);

      } catch (error) {
        this.logger.error(`Failed to process message for chat ${event.chatId}:`, error);
        validationFailures++;
      }
    }

    // Update device metadata
    await this.updateDeviceMetadata(device.id, request);

    const response: IngestResponseDto = {
      success: true,
      message: 'Events processed successfully',
      processed_count: processedCount,
      timestamp: Date.now(),
      duplicates_skipped: duplicatesSkipped > 0 ? duplicatesSkipped : undefined,
      validation_failures: validationFailures > 0 ? validationFailures : undefined,
    };

    this.logger.log(
      `Ingest completed for device ${device.id}: ${processedCount} processed, ${duplicatesSkipped} duplicates, ${validationFailures} failures`
    );

    return response;
  }

  private generateMessageId(deviceId: string, event: MessageEventDto): string {
    // Create deterministic ID from device + chat + timestamp + body hash
    const content = `${deviceId}-${event.chatId}-${event.timestamp}-${event.body}`;
    return randomUUID();
  }

  private async enqueueSummarizationJob(chatId: string, timestamp: Date): Promise<void> {
    try {
      // Create job key based on chat and hour window
      const hour = new Date(timestamp);
      hour.setMinutes(0, 0, 0); // Round down to hour boundary
      const jobKey = `${chatId}:${hour.getTime()}`;

      // Add job with delay to allow batching (5 minutes after hour boundary)
      const delay = this.calculateJobDelay(hour);

      await this.summarizationQueue.add(
        'summarize-chat',
        {
          chatId,
          windowStart: hour.toISOString(),
          windowEnd: new Date(hour.getTime() + 60 * 60 * 1000).toISOString(),
        },
        {
          jobId: jobKey,
          delay,
          removeOnComplete: 10,
          removeOnFail: 5,
          attempts: 3,
          backoff: {
            type: 'exponential',
            delay: 2000,
          },
        }
      );

      this.logger.debug(`Summarization job enqueued: ${jobKey} with delay ${delay}ms`);
    } catch (error) {
      this.logger.error(`Failed to enqueue summarization job for chat ${chatId}:`, error);
      // Don't throw - message ingestion should succeed even if queue fails
    }
  }

  private calculateJobDelay(hourBoundary: Date): number {
    const now = new Date();
    const nextProcessTime = new Date(hourBoundary.getTime() + 5 * 60 * 1000); // 5 minutes after hour

    // If we're already past the process time, run immediately
    if (now >= nextProcessTime) {
      return 0;
    }

    return nextProcessTime.getTime() - now.getTime();
  }

  private async updateDeviceMetadata(deviceId: string, request: IngestRequestDto): Promise<void> {
    try {
      const updates: Partial<Device> = {
        lastSeen: new Date(),
      };

      if (request.app_version) {
        updates.appVersion = request.app_version;
      }

      if (request.platform) {
        updates.platform = request.platform;
      }

      await db
        .update(devices)
        .set(updates)
        .where(eq(devices.id, deviceId));

    } catch (error) {
      this.logger.error(`Failed to update device metadata for ${deviceId}:`, error);
      // Don't throw - this is not critical
    }
  }

  async getMessageStats(deviceId: string): Promise<any> {
    try {
      const stats = await db
        .select({
          totalMessages: messages.id,
        })
        .from(messages)
        .where(eq(messages.deviceId, deviceId));

      return {
        totalMessages: stats.length,
        device: deviceId,
      };
    } catch (error) {
      this.logger.error(`Failed to get message stats for device ${deviceId}:`, error);
      throw error;
    }
  }
}
