package com.payments.graphql.scalar;

import com.payments.model.enums.CurrencyCode;
import graphql.language.StringValue;
import graphql.schema.*;

public final class CustomScalars {

    private CustomScalars() {}

    public static final GraphQLScalarType CURRENCY_SCALAR = GraphQLScalarType.newScalar()
            .name("Currency")
            .description("ISO 4217 currency code (e.g. USD, EUR, GBP)")
            .coercing(new Coercing<CurrencyCode, String>() {

                @Override
                public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                    if (dataFetcherResult instanceof CurrencyCode cc) {
                        return cc.name();
                    }
                    if (dataFetcherResult instanceof String s) {
                        return s;
                    }
                    throw new CoercingSerializeException("Expected CurrencyCode but got: " + dataFetcherResult);
                }

                @Override
                public CurrencyCode parseValue(Object input) throws CoercingParseValueException {
                    try {
                        return CurrencyCode.valueOf(input.toString().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new CoercingParseValueException("Invalid currency code: " + input);
                    }
                }

                @Override
                public CurrencyCode parseLiteral(Object input) throws CoercingParseLiteralException {
                    if (input instanceof StringValue sv) {
                        try {
                            return CurrencyCode.valueOf(sv.getValue().toUpperCase());
                        } catch (IllegalArgumentException e) {
                            throw new CoercingParseLiteralException("Invalid currency code: " + sv.getValue());
                        }
                    }
                    throw new CoercingParseLiteralException("Expected a string literal for Currency");
                }
            })
            .build();
}
