import {
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import {
  ApiTags,
  ApiOperation,
  ApiResponse,
} from '@nestjs/swagger';
import {
  HealthCheckService,
  HealthCheck,
  TypeOrmHealthIndicator,
  MemoryHealthIndicator,
  DiskHealthIndicator,
} from '@nestjs/terminus';
import { checkDatabaseHealth } from '@/database/connection';
import { OpenAIService } from '@/modules/summarization/openai.service';
import { TopicFilterService } from '@/modules/summarization/topic-filter.service';
import { InjectQueue } from '@nestjs/bullmq';
import { Queue } from 'bullmq';

@ApiTags('Health')
@Controller('health')
export class HealthController {
  private readonly logger = new Logger(HealthController.name);

  constructor(
    private readonly healthCheckService: HealthCheckService,
    private readonly memoryHealthIndicator: MemoryHealthIndicator,
    private readonly diskHealthIndicator: DiskHealthIndicator,
    private readonly openaiService: OpenAIService,
    private readonly topicFilterService: TopicFilterService,
    @InjectQueue('summarization') private summarizationQueue: Queue,
  ) {}

  @Get()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Health check endpoint',
    description: 'Returns the health status of all system components',
  })
  @ApiResponse({
    status: 200,
    description: 'Health check passed',
    schema: {
      type: 'object',
      properties: {
        status: { type: 'string', example: 'ok' },
        info: { type: 'object' },
        error: { type: 'object' },
        details: { type: 'object' },
      },
    },
  })
  @ApiResponse({
    status: 503,
    description: 'Health check failed',
  })
  @HealthCheck()
  async check() {
    return this.healthCheckService.check([
      // Database health
      () => this.checkDatabase(),

      // Memory health (heap should not exceed 500MB)
      () => this.memoryHealthIndicator.checkHeap('memory_heap', 500 * 1024 * 1024),

      // Disk health (disk should not exceed 90% usage)
      () => this.diskHealthIndicator.checkStorage('storage', {
        path: '/',
        thresholdPercent: 0.9,
      }),

      // Redis/Queue health
      () => this.checkRedisQueue(),

      // OpenAI service health
      () => this.checkOpenAI(),

      // BERT model health
      () => this.checkBERTModel(),
    ]);
  }

  @Get('ready')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Readiness probe',
    description: 'Returns readiness status for Kubernetes deployments',
  })
  @ApiResponse({
    status: 200,
    description: 'Service is ready',
  })
  @ApiResponse({
    status: 503,
    description: 'Service is not ready',
  })
  async readiness() {
    return this.healthCheckService.check([
      () => this.checkDatabase(),
      () => this.checkRedisQueue(),
    ]);
  }

  @Get('live')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Liveness probe',
    description: 'Returns liveness status for Kubernetes deployments',
  })
  @ApiResponse({
    status: 200,
    description: 'Service is alive',
  })
  async liveness() {
    return { status: 'ok', timestamp: new Date().toISOString() };
  }

  private async checkDatabase(): Promise<Record<string, any>> {
    try {
      const isHealthy = await checkDatabaseHealth();
      if (!isHealthy) {
        throw new Error('Database connection failed');
      }
      return {
        database: {
          status: 'up',
          message: 'Database connection successful',
        },
      };
    } catch (error) {
      this.logger.error('Database health check failed:', error);
      throw new Error(`Database health check failed: ${(error as Error).message}`);
    }
  }

  private async checkRedisQueue(): Promise<Record<string, any>> {
    try {
      // Check if Redis connection is working by getting queue info
      await this.summarizationQueue.getWaiting();
      return {
        redis_queue: {
          status: 'up',
          message: 'Redis queue connection successful',
        },
      };
    } catch (error) {
      this.logger.error('Redis queue health check failed:', error);
      throw new Error(`Redis queue health check failed: ${(error as Error).message}`);
    }
  }

  private async checkOpenAI(): Promise<Record<string, any>> {
    try {
      const isHealthy = await this.openaiService.checkHealth();
      if (!isHealthy) {
        throw new Error('OpenAI API connection failed');
      }
      return {
        openai: {
          status: 'up',
          message: 'OpenAI API connection successful',
        },
      };
    } catch (error) {
      this.logger.warn('OpenAI health check failed:', error);
      // Don't fail the overall health check for OpenAI issues
      return {
        openai: {
          status: 'degraded',
          message: `OpenAI API check failed: ${(error as Error).message}`,
        },
      };
    }
  }

  private async checkBERTModel(): Promise<Record<string, any>> {
    try {
      const isHealthy = await this.topicFilterService.healthCheck();
      const isInitialized = this.topicFilterService.isModelInitialized();

      return {
        bert_model: {
          status: isHealthy && isInitialized ? 'up' : 'degraded',
          message: isInitialized
            ? 'BERT model initialized and working'
            : 'BERT model not initialized (using fallback filtering)',
          initialized: isInitialized,
        },
      };
    } catch (error) {
      this.logger.warn('BERT model health check failed:', error);
      return {
        bert_model: {
          status: 'degraded',
          message: `BERT model check failed: ${(error as Error).message}`,
          initialized: false,
        },
      };
    }
  }
}
