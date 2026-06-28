<template>
  <view class="app-shell">
    <slot />
  </view>
</template>

<style lang="scss">
page {
  background: #f8f4ef;
  color: #2e2a27;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;
}

.app-shell {
  min-height: 100vh;
}
</style>
