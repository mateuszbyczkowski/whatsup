import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { BullModule } from '@nestjs/bullmq';
import { LoggerModule } from 'nestjs-pino';
import { environment } from './environments/environment';
import appConfig from './config/app.config';

// Import modules
import { MessagesModule } from './modules/messages/messages.module';
import { SummarizationModule } from './modules/summarization/summarization.module';
import { SummariesModule } from './modules/summaries/summaries.module';
import { HealthModule } from './modules/health/health.module';

@Module({
  imports: [
    // Configuration
    ConfigModule.forRoot({
      isGlobal: true,
      load: [appConfig],
      envFilePath: ['.env.local', '.env'],
    }),

    // Logging
    LoggerModule.forRoot({
      pinoHttp: {
        level: environment.logging.level,
        transport: environment.logging.pretty
          ? {
              target: 'pino-pretty',
              options: {
                colorize: true,
                singleLine: true,
                ignore: 'pid,hostname',
                translateTime: 'yyyy-mm-dd HH:MM:ss',
              },
            }
          : undefined,
        serializers: {
          req: (req) => ({
            method: req.method,
            url: req.url,
            userAgent: req.headers?.['user-agent'],
          }),
          res: (res) => ({
            statusCode: res.statusCode,
          }),
        },
      },
    }),

    // BullMQ for job queues
    BullModule.forRoot({
      connection: {
        host: environment.redis.host,
        port: environment.redis.port,
        password: environment.redis.password,
        db: environment.redis.db,
        retryDelayOnFailover: 100,
        enableReadyCheck: true,
        maxRetriesPerRequest: 3,
      },
      defaultJobOptions: {
        removeOnComplete: 10,
        removeOnFail: 5,
        attempts: 3,
        backoff: {
          type: 'exponential',
          delay: 2000,
        },
      },
    }),

    // Application modules
    MessagesModule,
    SummarizationModule,
    SummariesModule,
    HealthModule,
  ],
  controllers: [],
  providers: [],
})
export class AppModule {}
