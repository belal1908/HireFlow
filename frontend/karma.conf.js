// Karma configuration. Adds a ChromeHeadlessNoSandbox launcher (`--no-sandbox`) because the
// default ChromeHeadless launcher fails to capture in this sandboxed dev environment.
process.env.CHROME_BIN = process.env.CHROME_BIN || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma')
    ],
    client: {
      jasmine: {},
      clearContext: false
    },
    jasmineHtmlReporter: {
      suppressAll: true
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/frontend'),
      subdir: '.',
      reporters: [{ type: 'html' }, { type: 'text-summary' }]
    },
    reporters: ['progress', 'kjhtml'],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: true,
    // Raised from Karma's 60s default purely as headroom: on a loaded machine Chrome can take
    // longer than that to come up, which surfaces as a "has not captured in 60000 ms" failure
    // that looks like a real defect but isn't. Raising the ceiling changes no test behavior.
    captureTimeout: 180000,
    browserDisconnectTimeout: 30000,
    browserDisconnectTolerance: 2,
    browserNoActivityTimeout: 120000,
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage']
      }
    },
    browsers: ['ChromeHeadlessNoSandbox'],
    singleRun: false,
    restartOnFileChange: true
  });
};
