import { Module } from '@nestjs/common';
import { TerminusModule } from '@nestjs/terminus';
import { BullModule } from '@nestjs/bullmq';
import { HealthController } from './health.controller';
import { SummarizationModule } from '@/modules/summarization/summarization.module';

@Module({
  imports: [
    TerminusModule,
    BullModule.registerQueue({
      name: 'summarization',
    }),
    SummarizationModule,
  ],
  controllers: [HealthController],
})
export class HealthModule {}
