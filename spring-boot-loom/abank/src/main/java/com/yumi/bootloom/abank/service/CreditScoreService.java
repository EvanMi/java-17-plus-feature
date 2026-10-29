package com.yumi.bootloom.abank.service;

import com.yumi.bootloom.abank.domain.Model.*;
import com.yumi.bootloom.abank.domain.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.concurrent.StructuredTaskScope;

@Service
public class CreditScoreService {
    private static final Logger logger = LoggerFactory.getLogger(CreditScoreService.class);

    private final RestClient restClient;

    public CreditScoreService(RestClient restClient) {
        this.restClient = restClient;
    }

    public CreditScore getCreditScore(Customer customer) {
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<CreditScore>anySuccessfulResultOrThrow())) {
            scope.fork(() -> getCreditScoreFrom("credit-score1", customer));
            scope.fork(() -> getCreditScoreFrom("credit-score2", customer));

            CreditScore score = scope.join();
            return score;
        } catch (InterruptedException e) {
            throw new ABankException(e);
        }
    }

    private CreditScore getCreditScoreFrom(String endpoint, Customer customer) {
        var requestId = RequestContext.getRequestId();

        logger.info("{} CreditScoreService.getCreditScore() with {}: Start", requestId, endpoint);

        var score = restClient
                .get()
                .uri("/customer/{id}/{endpoint}", customer.id(), endpoint)
                .retrieve()
                .body(CreditScore.class);
        logger.info("{} CreditScoreService.getCreditScore() with {}: Done", requestId, endpoint);

        return score;
    }
}
