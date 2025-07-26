import { IsString, IsOptional, IsDateString, IsNumber, Min } from 'class-validator';
import { Type, Transform } from 'class-transformer';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class GetSummaryQueryDto {
  @ApiPropertyOptional({
    description: 'Start date for summary period (ISO 8601)',
    example: '2024-01-01T00:00:00.000Z',
  })
  @IsOptional()
  @IsDateString()
  from?: string;

  @ApiPropertyOptional({
    description: 'End date for summary period (ISO 8601)',
    example: '2024-01-31T23:59:59.999Z',
  })
  @IsOptional()
  @IsDateString()
  to?: string;

  @ApiPropertyOptional({
    description: 'Maximum number of summaries to return',
    example: 10,
    default: 50,
  })
  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(1)
  limit?: number;

  @ApiPropertyOptional({
    description: 'Number of summaries to skip for pagination',
    example: 0,
    default: 0,
  })
  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(0)
  offset?: number;

  @ApiPropertyOptional({
    description: 'Format for the response',
    example: 'json',
    enum: ['json', 'markdown'],
    default: 'json',
  })
  @IsOptional()
  @IsString()
  format?: 'json' | 'markdown';
}

export class SummaryDto {
  @ApiProperty({
    description: 'Unique summary identifier',
    example: 'summary_abc123',
  })
  id: string;

  @ApiProperty({
    description: 'Chat identifier',
    example: 'group_family_chat',
  })
  chatId: string;

  @ApiProperty({
    description: 'Generated summary text in markdown format',
    example: '## Summary\n\nThe family discussed weekend plans...',
  })
  summaryText: string;

  @ApiProperty({
    description: 'Start of summarized period',
    example: '2024-01-15T10:00:00.000Z',
  })
  periodStart: string;

  @ApiProperty({
    description: 'End of summarized period',
    example: '2024-01-15T11:00:00.000Z',
  })
  periodEnd: string;

  @ApiProperty({
    description: 'Number of messages summarized',
    example: 25,
  })
  messageCount: number;

  @ApiProperty({
    description: 'AI model used for summarization',
    example: 'gpt-4o-mini',
  })
  model: string;

  @ApiPropertyOptional({
    description: 'Number of tokens used for generation',
    example: 350,
  })
  tokensUsed?: number;

  @ApiProperty({
    description: 'When the summary was created',
    example: '2024-01-15T11:05:00.000Z',
  })
  createdAt: string;

  @ApiPropertyOptional({
    description: 'Additional metadata as JSON string',
    example: '{"originalMessageCount": 30, "filteredMessageCount": 25}',
  })
  metadata?: string;
}

export class GetSummariesResponseDto {
  @ApiProperty({
    description: 'Array of summaries for the requested chat and period',
    type: [SummaryDto],
  })
  summaries: SummaryDto[];

  @ApiProperty({
    description: 'Chat identifier that was queried',
    example: 'group_family_chat',
  })
  chatId: string;

  @ApiProperty({
    description: 'Total number of summaries available (for pagination)',
    example: 120,
  })
  total: number;

  @ApiProperty({
    description: 'Number of summaries returned in this response',
    example: 10,
  })
  count: number;

  @ApiProperty({
    description: 'Query parameters used',
  })
  query: {
    from?: string;
    to?: string;
    limit: number;
    offset: number;
  };

  @ApiProperty({
    description: 'Response timestamp',
    example: 1640995260000,
  })
  timestamp: number;
}

export class GetSummaryMarkdownResponseDto {
  @ApiProperty({
    description: 'Combined summaries formatted as markdown digest',
    example: '# WhatsApp Digest for Family Chat\n\n## January 15, 2024 (10:00-11:00)\n\nThe family discussed...',
  })
  markdown: string;

  @ApiProperty({
    description: 'Chat identifier',
    example: 'group_family_chat',
  })
  chatId: string;

  @ApiProperty({
    description: 'Period covered by the digest',
  })
  period: {
    from: string;
    to: string;
  };

  @ApiProperty({
    description: 'Summary statistics',
  })
  stats: {
    summaryCount: number;
    totalMessages: number;
    timeSpan: string;
  };

  @ApiProperty({
    description: 'Response timestamp',
    example: 1640995260000,
  })
  timestamp: number;
}

export class ChatListDto {
  @ApiProperty({
    description: 'Unique chat identifier',
    example: 'group_family_chat',
  })
  chatId: string;

  @ApiProperty({
    description: 'Number of summaries available for this chat',
    example: 45,
  })
  summaryCount: number;

  @ApiProperty({
    description: 'Most recent summary date',
    example: '2024-01-15T11:05:00.000Z',
  })
  lastSummaryAt: string;

  @ApiProperty({
    description: 'First summary date',
    example: '2024-01-01T09:15:00.000Z',
  })
  firstSummaryAt: string;

  @ApiProperty({
    description: 'Total messages processed for this chat',
    example: 1250,
  })
  totalMessages: number;
}

export class GetChatsResponseDto {
  @ApiProperty({
    description: 'List of chats with summary statistics',
    type: [ChatListDto],
  })
  chats: ChatListDto[];

  @ApiProperty({
    description: 'Total number of unique chats',
    example: 8,
  })
  total: number;

  @ApiProperty({
    description: 'Response timestamp',
    example: 1640995260000,
  })
  timestamp: number;
}
