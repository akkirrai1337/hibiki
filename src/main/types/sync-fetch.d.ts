declare module "sync-fetch" {
  interface SyncResponse {
    status: number;
    ok: boolean;
    text(): string;
    headers: {
      forEach(callback: (value: string, key: string) => void): void;
    };
  }

  interface SyncFetchOptions {
    method?: string;
    headers?: Record<string, string>;
    body?: string;
  }

  function syncFetch(url: string, options?: SyncFetchOptions): SyncResponse;
  export default syncFetch;
}
