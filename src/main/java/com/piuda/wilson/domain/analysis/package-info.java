/**
 * 분석(Analysis) 도메인 — 조기 발견 보조 시스템.
 *
 * <p>일상 대화 데이터(기억력·어휘력·감정 변화)를 백그라운드에서 분석하고 이상 징후를 스코어링한다.
 * 무거운 분석은 Kafka 기반 비동기 파이프라인으로 처리한다.</p>
 *
 * <p>계층: controller → service → repository → entity, 요청/응답은 dto 패키지.</p>
 */
package com.piuda.wilson.domain.analysis;
