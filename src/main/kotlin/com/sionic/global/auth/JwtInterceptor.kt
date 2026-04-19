package com.sionic.global.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.sionic.domain.user.enums.Role
import com.sionic.global.exception.ErrorCode
import com.sionic.global.response.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class JwtInterceptor(
    private val jwtProvider: JwtProvider,
    private val objectMapper: ObjectMapper,
) : HandlerInterceptor {

    companion object {
        const val AUTH_USER_ATTR = "authUser"
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val token = extractToken(request) ?: return unauthorized(response)
        val authUser = jwtProvider.parse(token) ?: return unauthorized(response)
        if (handler is HandlerMethod && !isAuthorized(handler, authUser.role)) {
            return forbidden(response)
        }
        request.setAttribute(AUTH_USER_ATTR, authUser)
        return true
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.removePrefix("Bearer ").trim()
    }

    private fun unauthorized(response: HttpServletResponse): Boolean {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        val body = ErrorResponse.of(ErrorCode.UNAUTHORIZED)
        response.writer.write(objectMapper.writeValueAsString(body))
        return false
    }

    private fun forbidden(response: HttpServletResponse): Boolean {
        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        val body = ErrorResponse.of(ErrorCode.FORBIDDEN)
        response.writer.write(objectMapper.writeValueAsString(body))
        return false
    }

    private fun isAuthorized(handler: HandlerMethod, role: Role): Boolean {
        val requiredRoles = handler.getMethodAnnotation(RequiredRole::class.java)?.value
            ?: handler.beanType.getAnnotation(RequiredRole::class.java)?.value
            ?: return true

        return role in requiredRoles
    }
}
