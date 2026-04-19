package com.sionic.global.auth

import com.sionic.domain.user.enums.Role

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiredRole(vararg val value: Role)
