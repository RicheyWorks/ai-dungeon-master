package com.xai.dungeonmaster.web;

import com.xai.dungeonmaster.config.RequestIdFilter;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.dto.ErrorPayload;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * Maps common Spring MVC failures to v2 error envelopes with requestId.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Envelope<?>> badJson(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "Malformed JSON request body.", req);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentNotValidException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Envelope<?>> badRequest(Exception ex, HttpServletRequest req) {
        String msg = ex.getMessage() == null ? "Bad request." : ex.getMessage();
        if (msg.length() > 300) msg = msg.substring(0, 300);
        return error(HttpStatus.BAD_REQUEST, msg, req);
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public ResponseEntity<Envelope<?>> tooLarge(Exception ex, HttpServletRequest req) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "Upload too large.", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Envelope<?>> generic(Exception ex, HttpServletRequest req) {
        // Avoid leaking internals; log message is enough for operators via stack in server logs.
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error.", req);
    }

    private static ResponseEntity<Envelope<?>> error(HttpStatus status, String message, HttpServletRequest req) {
        String requestId = RequestIdFilter.resolve(req);
        return ResponseEntity.status(status)
                .body(Envelope.of("error", new ErrorPayload(message), requestId));
    }
}
