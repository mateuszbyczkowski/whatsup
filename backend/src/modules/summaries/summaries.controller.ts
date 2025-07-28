import {
  Controller,
  Get,
  Param,
  Query,
  UseGuards,
  HttpCode,
  HttpStatus,
  Logger,
  BadRequestException,
  Post,
  Body,
} from '@nestjs/common';
import {
  ApiTags,
  ApiOperation,
  ApiResponse,
  ApiBearerAuth,
  ApiParam,
  ApiQuery,
  ApiBody,
} from '@nestjs/swagger';
import { SummariesService } from './summaries.service';
import { DeviceAuthGuard } from '@/shared/auth.guard';
import { CurrentDevice } from '@/shared/device.decorator';
import { Device } from '@/database/schema';
import {
  GetSummaryQueryDto,
  GetSummariesResponseDto,
  GetSummaryMarkdownResponseDto,
  GetChatsResponseDto,
} from './dto/summary.dto';

@ApiTags('Summaries')
@Controller('summaries')
@UseGuards(DeviceAuthGuard)
@ApiBearerAuth()
export class SummariesController {
  private readonly logger = new Logger(SummariesController.name);

  constructor(private readonly summariesService: SummariesService) {}

  @Get('chats')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Get list of available chats with summaries',
    description: 'Returns all chats that have generated summaries for the authenticated device',
  })
  @ApiResponse({
    status: 200,
    description: 'List of chats with summary statistics',
    type: GetChatsResponseDto,
  })
  @ApiResponse({
    status: 401,
    description: 'Invalid or missing device token',
  })
  async getAvailableChats(@CurrentDevice() device: Device): Promise<GetChatsResponseDto> {
    this.logger.log(`Getting available chats for device ${device.id}`);
    return await this.summariesService.getAvailableChats(device.id);
  }

  @Get('stats')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Get summary statistics for the device',
    description: 'Returns overall statistics about messages and summaries for the authenticated device',
  })
  @ApiResponse({
    status: 200,
    description: 'Summary statistics',
  })
  @ApiResponse({
    status: 401,
    description: 'Invalid or missing device token',
  })
  async getSummaryStats(@CurrentDevice() device: Device): Promise<any> {
    this.logger.log(`Getting summary stats for device ${device.id}`);
    return await this.summariesService.getSummaryStats(device.id);
  }

  @Get(':chatId')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Get summaries for a specific chat',
    description: 'Returns summaries for the specified chat with optional date filtering and pagination',
  })
  @ApiParam({
    name: 'chatId',
    description: 'Chat identifier (e.g., group_family_chat)',
    example: 'group_family_chat',
  })
  @ApiQuery({
    name: 'from',
    required: false,
    description: 'Start date for summary period (ISO 8601)',
    example: '2024-01-01T00:00:00.000Z',
  })
  @ApiQuery({
    name: 'to',
    required: false,
    description: 'End date for summary period (ISO 8601)',
    example: '2024-01-31T23:59:59.999Z',
  })
  @ApiQuery({
    name: 'limit',
    required: false,
    description: 'Maximum number of summaries to return',
    example: 10,
  })
  @ApiQuery({
    name: 'offset',
    required: false,
    description: 'Number of summaries to skip for pagination',
    example: 0,
  })
  @ApiQuery({
    name: 'format',
    required: false,
    description: 'Response format',
    enum: ['json', 'markdown'],
    example: 'json',
  })
  @ApiResponse({
    status: 200,
    description: 'Summaries retrieved successfully',
    type: GetSummariesResponseDto,
  })
  @ApiResponse({
    status: 400,
    description: 'Invalid query parameters',
  })
  @ApiResponse({
    status: 401,
    description: 'Invalid or missing device token',
  })
  @ApiResponse({
    status: 404,
    description: 'Chat not found or no summaries available',
  })
  async getSummariesForChat(
    @Param('chatId') chatId: string,
    @Query() query: GetSummaryQueryDto,
    @CurrentDevice() device: Device,
  ): Promise<GetSummariesResponseDto | GetSummaryMarkdownResponseDto> {
    this.logger.log(`Getting summaries for chat ${chatId}, device ${device.id}`);

    // Validate chatId format
    if (!chatId || chatId.trim().length === 0) {
      throw new BadRequestException('Chat ID is required and cannot be empty');
    }

    // Validate date range if provided
    if (query.from && query.to) {
      const fromDate = new Date(query.from);
      const toDate = new Date(query.to);

      if (fromDate >= toDate) {
        throw new BadRequestException('From date must be before to date');
      }
    }

    // Return markdown format if requested
    if (query.format === 'markdown') {
      return await this.summariesService.getSummariesAsMarkdown(chatId, device.id, query);
    }

    // Default to JSON format
    return await this.summariesService.getSummariesForChat(chatId, device.id, query);
  }

  @Post('trigger')
  @HttpCode(HttpStatus.ACCEPTED)
  @ApiOperation({
    summary: 'Manually trigger summarization for the authenticated device',
    description: 'Triggers summarization for all chats with messages for the authenticated device. Optionally, provide chatId in body to trigger for a specific chat only.',
  })
  @ApiBody({
    schema: {
      type: 'object',
      properties: {
        chatId: { type: 'string', description: 'Chat identifier (optional)' },
      },
    },
    required: false,
  })
  @ApiResponse({ status: 202, description: 'Summarization triggered' })
  @ApiResponse({ status: 401, description: 'Invalid or missing device token' })
  async triggerSummarization(
    @CurrentDevice() device: Device,
    @Body() body: { chatId?: string }
  ): Promise<{ status: string; chatId?: string }> {
    this.logger.log(`Triggering summarization for device ${device.id}${body?.chatId ? ', chat ' + body.chatId : ''}`);
    await this.summariesService.triggerSummarization(device.id, body?.chatId);
    return { status: 'summarization triggered', chatId: body?.chatId };
  }
}
