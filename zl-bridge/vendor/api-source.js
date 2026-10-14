// api-source 桩：取链不走内置 API，统一走 userApi 脚本（见 userapi-host.js）
export const apis = () => ({
  getMusicUrl: async () => { throw new Error('内置音源 API 已禁用，请走 userApi 脚本') },
})
