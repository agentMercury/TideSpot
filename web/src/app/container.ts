/**
 * 依赖装配（对应 Android `:app/AppGraph.kt`）。
 * 生产单例；测试用注入替代。
 */
import { OpenMeteoClient } from '../data/client';
import { TideSpotRepository } from '../data/repository';

export const client = new OpenMeteoClient();

export const repository = new TideSpotRepository({ client });

/** 与 Android versionName 对齐（C-7） */
export const APP_VERSION = '1.18.3';