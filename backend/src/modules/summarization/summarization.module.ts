import { Module } from '@nestjs/common';
import { BullModule } from '@nestjs/bullmq';
import { SummarizationProcessor } from './summarization.processor';
import { OpenAIService } from './openai.service';
import { TopicFilterService } from './topic-filter.service';

@Module({
  imports: [
    BullModule.registerQueue({
      name: 'summarization',
    }),
  ],
  providers: [
    SummarizationProcessor,
    OpenAIService,
    TopicFilterService,
  ],
  exports: [
    OpenAIService,
    TopicFilterService,
  ],
})
export class SummarizationModule {}
