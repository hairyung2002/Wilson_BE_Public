package com.piuda.wilson.infrastructure.ai;

/**
 * AI가 노인 발화에서 감지한 응급 신호의 종류 (api-spec 15-1, 결정 G1).
 * gRPC 계약: {@code StreamResponse.emergency} (결정 J5, docs/ai-integration-contract.md 1-3).
 */
public enum EmergencyCategory {
    FALL,
    PAIN,
    BREATHING,
    HELP_REQUEST,
    OTHER
}
