package com.fantasta.exception;

import com.fantasta.dto.ErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable exception) {
        ErrorResponse error;

        if (exception instanceof WebApplicationException webEx) {
            // eccezioni REST standard (BadRequest, Forbidden, ecc.)
            String code = switch (webEx.getResponse().getStatus()) {
                case 400 -> "BAD_REQUEST";
                case 401 -> "UNAUTHORIZED";
                case 403 -> "FORBIDDEN";
                case 404 -> "NOT_FOUND";
                default -> "GENERIC_ERROR";
            };
            error = new ErrorResponse(code, webEx.getMessage());
            return Response.status(webEx.getResponse().getStatus())
                    .entity(error)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        // fallback per eccezioni non gestite
        error = new ErrorResponse("INTERNAL_ERROR", exception.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(error)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}

