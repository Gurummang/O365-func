package com.GASB.o365_func.annotation.JWT;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

@Data
public class JwtValidationResponse implements Serializable {
    private final String email;
    private final String status;

    @JsonCreator
    public JwtValidationResponse(@JsonProperty("email") String email,
                                 @JsonProperty("status") String status) {
        this.email = email;
        this.status = status;
    }
}

