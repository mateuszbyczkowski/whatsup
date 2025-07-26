import { IsString, IsNumber, IsArray, ValidateNested, IsOptional, IsNotEmpty, Min, Max } from 'class-validator';
import { Type } from 'class-transformer';
import { ApiProperty } from '@nestjs/swagger';

export class MessageEventDto {
  @ApiProperty({
    description: 'Chat identifier generated from group/contact name',
    example: 'group_family_chat',
  })
  @IsString()
  @IsNotEmpty()
  chatId: string;

  @ApiProperty({
    description: 'Sender name from WhatsApp notification',
    example: 'John Doe',
  })
  @IsString()
  @IsNotEmpty()
  sender: string;

  @ApiProperty({
    description: 'Message content',
    example: 'Hey everyone, how is everyone doing?',
  })
  @IsString()
  @IsNotEmpty()
  body: string;

  @ApiProperty({
    description: 'Original message timestamp (Unix milliseconds)',
    example: 1640995200000,
  })
  @IsNumber()
  @Min(0)
  timestamp: number;

  @ApiProperty({
    description: 'WhatsApp package name',
    example: 'com.whatsapp',
    enum: ['com.whatsapp', 'com.whatsapp.w4b'],
  })
  @IsString()
  @IsNotEmpty()
  packageName: string;
}

export class IngestRequestDto {
  @ApiProperty({
    description: 'Device identifier',
    example: 'device_abc123',
  })
  @IsString()
  @IsNotEmpty()
  device_id: string;

  @ApiProperty({
    description: 'Array of WhatsApp message events',
    type: [MessageEventDto],
  })
  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => MessageEventDto)
  events: MessageEventDto[];

  @ApiProperty({
    description: 'Sync timestamp when batch was sent (Unix milliseconds)',
    example: 1640995260000,
  })
  @IsNumber()
  @Min(0)
  timestamp: number;

  @ApiProperty({
    description: 'Number of events in this batch',
    example: 5,
  })
  @IsNumber()
  @Min(1)
  @Max(1000)
  batch_size: number;

  @ApiProperty({
    description: 'Android app version',
    example: '1.0.0',
    required: false,
  })
  @IsOptional()
  @IsString()
  app_version?: string;

  @ApiProperty({
    description: 'Platform identifier',
    example: 'android',
    default: 'android',
  })
  @IsOptional()
  @IsString()
  platform?: string;
}

export class IngestResponseDto {
  @ApiProperty({
    description: 'Whether the ingestion was successful',
    example: true,
  })
  success: boolean;

  @ApiProperty({
    description: 'Response message',
    example: 'Events processed successfully',
  })
  message: string;

  @ApiProperty({
    description: 'Number of events that were processed',
    example: 5,
  })
  processed_count: number;

  @ApiProperty({
    description: 'Server timestamp when processing completed',
    example: 1640995260000,
  })
  timestamp: number;

  @ApiProperty({
    description: 'Number of duplicate events that were skipped',
    example: 0,
    required: false,
  })
  duplicates_skipped?: number;

  @ApiProperty({
    description: 'Number of events that failed validation',
    example: 0,
    required: false,
  })
  validation_failures?: number;
}
