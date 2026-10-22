// Browser semantics tests wait for real animation frames, rather than a virtual test clock.
config.client = {
    ...config.client,
    mocha: {
        ...config.client?.mocha,
        timeout: 15000,
    },
};

config.customLaunchers = {
    ...config.customLaunchers,
    ChromeHeadlessWebGL: {
        base: 'ChromeHeadless',
        flags: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
    },
};
config.browsers = ['ChromeHeadlessWebGL'];
