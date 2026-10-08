package com.waytun.app.vpn

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WireGuardBackend

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AmneziaBackend
