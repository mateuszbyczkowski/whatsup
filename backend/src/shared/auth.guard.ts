import {
  Injectable,
  CanActivate,
  ExecutionContext,
  UnauthorizedException,
  Logger,
} from '@nestjs/common';
import { Request } from 'express';
import { createHash } from 'crypto';
import { db } from '@/database/connection';
import { devices } from '@/database/schema';
import { eq } from 'drizzle-orm';
import { environment } from '@/environments/environment';

@Injectable()
export class DeviceAuthGuard implements CanActivate {
  private readonly logger = new Logger(DeviceAuthGuard.name);

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest<Request>();
    const token = this.extractTokenFromHeader(request);

    if (!token) {
      throw new UnauthorizedException('Device token is required');
    }

    try {
      // Hash the token with salt for secure comparison
      const tokenHash = this.hashToken(token);

      // Find device by token hash
      const device = await db
        .select()
        .from(devices)
        .where(eq(devices.tokenHash, tokenHash))
        .limit(1);

      if (!device.length) {
        this.logger.warn(`Authentication failed for token hash: ${tokenHash.substring(0, 8)}...`);
        throw new UnauthorizedException('Invalid device token');
      }

      // Update last seen timestamp
      await db
        .update(devices)
        .set({ lastSeen: new Date() })
        .where(eq(devices.id, device[0].id));

      // Attach device info to request for use in controllers
      request['device'] = device[0];

      this.logger.debug(`Device authenticated: ${device[0].id}`);
      return true;
    } catch (error) {
      if (error instanceof UnauthorizedException) {
        throw error;
      }

      this.logger.error('Authentication error:', error);
      throw new UnauthorizedException('Authentication failed');
    }
  }

  private extractTokenFromHeader(request: Request): string | undefined {
    const authHeader = request.headers.authorization;
    if (!authHeader) {
      return undefined;
    }

    const [type, token] = authHeader.split(' ') ?? [];
    return type === 'Bearer' ? token : undefined;
  }

  private hashToken(token: string): string {
    return createHash('sha256')
      .update(token + environment.auth.deviceTokenSalt)
      .digest('hex');
  }
}
