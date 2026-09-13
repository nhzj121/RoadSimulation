package org.example.roadsimulation.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;

/** Keeps simulation minute fields integer-token-only without changing global Jackson coercion. */
public class StrictIntegerDeserializer extends JsonDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            throw MismatchedInputException.from(parser, Integer.class, "expected an integer JSON number");
        }
        return parser.getIntValue();
    }
}
