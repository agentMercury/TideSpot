/**
 * 与 Android `core/data/DataError.kt` 对齐的结构化错误。
 * 分类依据 F-4.5：429 / 5xx / 网络错误可重试；参数拼写错误与其余 4xx 立即抛出。
 */

export type DataErrorKind =
  | 'RateLimited'
  | 'ServerError'
  | 'NetworkError'
  | 'SourceParamError'
  | 'BadRequest';

export class DataError extends Error {
  readonly kind: DataErrorKind;
  readonly httpStatus?: number;
  readonly reason?: string | null;
  readonly retryAfterSec?: number | null;

  constructor(
    kind: DataErrorKind,
    message: string,
    options: { httpStatus?: number; reason?: string | null; retryAfterSec?: number | null; cause?: unknown } = {},
  ) {
    super(message);
    this.name = `DataError.${kind}`;
    this.kind = kind;
    this.httpStatus = options.httpStatus;
    this.reason = options.reason ?? null;
    this.retryAfterSec = options.retryAfterSec ?? null;
    if (options.cause !== undefined) this.cause = options.cause;
  }

  get isRetryable(): boolean {
    return this.kind === 'RateLimited' || this.kind === 'ServerError' || this.kind === 'NetworkError';
  }
}

/** B9 已实测的参数错误文案片段 */
export const PARAM_REASON_PATTERNS = [
  'Cannot initialize',
  'Wrong Time Zone',
  'No data was found',
  'not a valid station',
];

export function extractReason(body: string | null | undefined): string | null {
  if (body == null || body.trim() === '') return null;
  try {
    const obj = JSON.parse(body) as Record<string, unknown>;
    const err = obj.error;
    if (err != null && typeof err === 'object') {
      const message = (err as Record<string, unknown>).message;
      if (typeof message === 'string') return message;
    }
    const reason = obj.reason;
    if (typeof reason === 'string') return reason;
    return body;
  } catch {
    return body;
  }
}

export function classify(status: number, body?: string | null): DataError {
  if (status === 429) {
    return new DataError('RateLimited', `数据源限流(429)`, { httpStatus: status });
  }
  if (status >= 500 && status <= 599) {
    return new DataError('ServerError', `数据源服务端错误(${status}): ${extractReason(body) ?? '无详情'}`, {
      httpStatus: status,
      reason: extractReason(body),
    });
  }
  if (status >= 400 && status <= 499) {
    const reason = extractReason(body);
    const isParam = reason != null && PARAM_REASON_PATTERNS.some((p) => reason.toLowerCase().includes(p.toLowerCase()));
    return isParam
      ? new DataError('SourceParamError', `数据源参数错误(B9): ${reason}`, { httpStatus: status, reason })
      : new DataError('BadRequest', `请求被拒绝(${status}): ${reason ?? '无详情'}`, { httpStatus: status, reason });
  }
  return new DataError('BadRequest', `请求被拒绝(${status}): ${extractReason(body) ?? '无详情'}`, {
    httpStatus: status,
    reason: extractReason(body),
  });
}