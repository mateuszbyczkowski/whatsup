import { Module } from '@nestjs/common';
import { BullModule } from '@nestjs/bullmq';
import { MessagesController } from './messages.controller';
import { MessagesService } from './messages.service';

@Module({
  imports: [
    BullModule.registerQueue({
      name: 'summarization',
    }),
  ],
  controllers: [MessagesController],
  providers: [MessagesService],
  exports: [MessagesService],
})
export class MessagesModule {}
