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

@media (min-width: 560px) {
  body {
    margin: 0;
    background:
      radial-gradient(circle at 18% 10%, rgba(217, 167, 160, 0.18), transparent 30%),
      radial-gradient(circle at 82% 18%, rgba(200, 163, 108, 0.14), transparent 28%),
      #f3ece6;
  }

  #app {
    width: 430px;
    min-height: 100vh;
    margin: 0 auto;
    overflow-x: hidden;
    background: #f8f4ef;
    box-shadow: 0 0 54px rgba(83, 55, 47, 0.14);
  }

  uni-page-head,
  .uni-tabbar-bottom .uni-tabbar,
  .uni-tabbar-top .uni-tabbar {
    width: 430px;
    max-width: 430px;
    left: 50%;
    right: auto;
    transform: translateX(-50%);
  }
}
</style>
