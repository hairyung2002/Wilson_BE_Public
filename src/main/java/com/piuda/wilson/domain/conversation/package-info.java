/**
 * 대화(Conversation) 도메인.
 *
 * <p>대화 세션과 메시지 이력을 관리한다. 진행 중 대화 컨텍스트는 Redis 를 활용한다.</p>
 *
 * <p>계층: controller → service → repository → entity, 요청/응답은 dto 패키지.</p>
 */
package com.piuda.wilson.domain.conversation;
