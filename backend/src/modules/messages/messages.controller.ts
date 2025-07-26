import {
  Controller,
  Post,
  Body,
  UseGuards,
  HttpCode,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import {
  ApiTags,
  ApiOperation,
  ApiResponse,
  ApiBearerAuth,
  ApiBody,
} from '@nestjs/swagger';
import { MessagesService } from './messages.service';
import { DeviceAuthGuard } from '@/shared/auth.guard';
import { CurrentDevice } from '@/shared/device.decorator';
import { Device } from '@/database/schema';
import { IngestRequestDto, IngestResponseDto } from './dto/ingest.dto';

@ApiTags('Messages')
@Controller('messages')
@UseGuards(DeviceAuthGuard)
@ApiBearerAuth()
export class MessagesController {
  private readonly logger = new Logger(MessagesController.name);

  constructor(private readonly messagesService: MessagesService) {}

  @Post('ingest')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Ingest WhatsApp messages from Android device',
    description: 'Receives batched WhatsApp notifications from the Android collector app and stores them for summarization',
  })
  @ApiBody({
    type: IngestRequestDto,
    description: 'Batch of WhatsApp message events with device metadata',
  })
  @ApiResponse({
    status: 200,
    description: 'Messages processed successfully',
    type: IngestResponseDto,
  })
  @ApiResponse({
    status: 400,
    description: 'Invalid request data or device ID mismatch',
  })
  @ApiResponse({
    status: 401,
    description: 'Invalid or missing device token',
  })
  @ApiResponse({
    status: 429,
    description: 'Rate limit exceeded',
  })
  async ingestMessages(
    @Body() ingestRequest: IngestRequestDto,
    @CurrentDevice() device: Device,
  ): Promise<IngestResponseDto> {
    this.logger.log(
      `Ingest request from device ${device.id}: ${ingestRequest.events.length} messages`
    );

    return await this.messagesService.ingestMessages(ingestRequest, device);
  }
}
