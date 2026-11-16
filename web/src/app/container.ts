/**
 * 依赖装配（对应 Android `:app/AppGraph.kt`）。
 * 生产单例；测试用注入替代。
 */
import { OpenMeteoClient } from '../data/client';
import { TideSpotRepository } from '../data/repository';

export const client = new OpenMeteoClient();

export const repository = new TideSpotRepository({ client });

/** Web 端独立版本号（与 Android 各自主管；评分内核仍强制逐字段一致） */
export const APP_VERSION = '1.18.3';