package com.app.pharmacy.domain;

import com.app.pharmacy.domain.dtos.common.ApiResponse;
import com.app.pharmacy.domain.dtos.common.RefSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The uniform response envelope. Every controller and every error path wraps
 * its payload in this, so a client parses one shape for the whole API — which
 * only holds if {@code success} is reliably true for data and false for
 * errors, and if an error never smuggles a payload into {@code data} by
 * accident.
 */
@DisplayName("ApiResponse — the uniform envelope")
class ApiResponseTest {

    @Test
    @DisplayName("a success carries the payload and flags success=true")
    void successCarriesPayload() {
        ApiResponse<String> response = ApiResponse.success("payload", "Retrieved successfully");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.message()).isEqualTo("Retrieved successfully");
    }

    @Test
    @DisplayName("the convenience overload supplies a default message")
    void successHasDefaultMessage() {
        ApiResponse<String> response = ApiResponse.success("payload");

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Request completed successfully");
    }

    @Test
    @DisplayName("a success with no payload is still a success — 'nothing to return' is not failure")
    void successAllowsNullData() {
        ApiResponse<Void> response = ApiResponse.success(null, "Password changed successfully");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNull();
    }

    @Test
    @DisplayName("an error flags success=false and carries no data")
    void errorCarriesNoData() {
        ApiResponse<String> response = ApiResponse.error("Something went wrong");

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.message()).isEqualTo("Something went wrong");
    }

    @Test
    @DisplayName("an error may still carry field-level detail for the client to render")
    void errorMayCarryDetail() {
        ApiResponse<List<String>> response = ApiResponse.error(
                List.of("email must be valid"), "Validation failed");

        assertThat(response.success()).isFalse();
        assertThat(response.data()).containsExactly("email must be valid");
    }

    @Test
    @DisplayName("every envelope is stamped with a parseable ISO-8601 timestamp")
    void timestampIsIso8601() {
        ApiResponse<String> response = ApiResponse.success("payload");

        assertThat(response.timestamp()).isNotBlank();
        assertThatCode(() -> Instant.parse(response.timestamp())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the envelope is generic over any payload, including nested DTOs")
    void wrapsArbitraryPayloads() {
        RefSummary ref = new RefSummary(UUID.randomUUID(), "Paracetamol");

        ApiResponse<List<RefSummary>> response = ApiResponse.success(List.of(ref));

        assertThat(response.data()).containsExactly(ref);
    }
}
