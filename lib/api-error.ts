/** 独立于 Next.js 运行时的业务错误，供后台函数与 API 路由共用 */
export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}
