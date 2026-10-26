/**
 * 인증(Auth) 도메인.
 *
 * <p>로그인 등 인증·인가 유스케이스를 담당한다. JWT/OAuth 등은 요청받은 이후에만 구현한다(YAGNI).</p>
 *
 * <p>계층: controller → service → repository → entity, 요청/응답은 dto 패키지.</p>
 */
package com.piuda.wilson.domain.auth;
