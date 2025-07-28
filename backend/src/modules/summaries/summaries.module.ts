import { Module } from '@nestjs/common';
import { BullModule } from '@nestjs/bullmq';
import { SummariesController } from './summaries.controller';
import { SummariesService } from './summaries.service';

@Module({
  imports: [
    BullModule.registerQueue({
      name: 'summarization',
    }),
  ],
  controllers: [SummariesController],
  providers: [SummariesService],
  exports: [SummariesService],
})
export class SummariesModule {}
