package com.yumi.bootloom.abank.domain;

import java.util.List;

public interface Model {

    record LoanApplicationRequest(String customerId, String amount, String purpose) { }
    record Customer(String id) { }
    record Account(String number, String balance) { }
    record Loan(String number, String amount) { }
    record CreditScore(String score) { }
    record LoanOfferRequest(List<Account> accounts, List<Loan> loans, String creditScore, String amount, String purpose) { }
    record Offer(String id, String amount, String purpose, String interest, String offerText) { }
    record FuncRole(String code, String roleName, String creator){}
    class ABankException extends RuntimeException {
        public ABankException(String message) {
            super(message);
        }

        public ABankException(Throwable th) {
            super(th);
        }
    }
}
