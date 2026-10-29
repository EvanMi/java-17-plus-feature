package com.yumi.bootloom.abank.controller;

import com.yumi.bootloom.abank.domain.Model.*;
import com.yumi.bootloom.abank.domain.RequestContext;
import com.yumi.bootloom.abank.service.AccountService;
import com.yumi.bootloom.abank.service.CreditScoreService;
import com.yumi.bootloom.abank.service.CustomerService;
import com.yumi.bootloom.abank.service.LoanService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.StructuredTaskScope;

@RestController
public class LoanController {
    private final CustomerService customerService;
    private final AccountService accountService;
    private final LoanService loanService;
    private final CreditScoreService creditScoreService;

    public LoanController(CustomerService customerService,
                          AccountService accountService,
                          LoanService loanService,
                          CreditScoreService creditScoreService) {
        this.customerService = customerService;
        this.accountService = accountService;
        this.loanService = loanService;
        this.creditScoreService = creditScoreService;
    }

    @PostMapping("/loan-application")
    public Offer applyForLoan(@RequestBody LoanApplicationRequest request) {
        var requestID = UUID.randomUUID();

        return RequestContext.withRequestId(requestID)
                .call(() -> {
                    var currentCustomer = customerService.getCustomer(request.customerId());

                    var customerInfo = getCustomerInfo(currentCustomer);
                    return loanService.calculateOffer(
                            currentCustomer, customerInfo.accounts(), customerInfo.loans(), customerInfo.creditScore(), request.amount(), request.purpose()
                    );
                });
    }

    private record CustomerInfo(List<Account> accounts, List<Loan> loans, CreditScore creditScore) { }
    private CustomerInfo getCustomerInfo(Customer customer) {
        try (var scope = StructuredTaskScope.open()) {
            var task1 = scope.fork(() -> accountService.getAccountsInfo(customer));
            var task2 = scope.fork(() -> loanService.getLoansInfo(customer));
            var task3 = scope.fork(() -> creditScoreService.getCreditScore(customer));

            scope.join();
            var accountsInfo = task1.get();
            var loansInfo = task2.get();
            var creditScore = task3.get();

            return new CustomerInfo(accountsInfo, loansInfo, creditScore);
        } catch (InterruptedException e) {
            throw new ABankException(e);
        }
    }
}
