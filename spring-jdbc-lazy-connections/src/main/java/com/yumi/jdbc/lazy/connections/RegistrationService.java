package com.yumi.jdbc.lazy.connections;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Slow external work, then a single save. Under eager a connection is pinned for the whole
// remote call; under lazy it's borrowed only for the save. Watch hikaricp.connections.usage.
@Service
class RegistrationService {

    private final SlowRemoteClient remoteClient;
    private final CustomerRepository repository;

    RegistrationService(SlowRemoteClient remoteClient, CustomerRepository repository) {
        this.remoteClient = remoteClient;
        this.repository = repository;
    }

    @Transactional
    public Customer register(long userId) {
        // 非常慢的请求，耗时很长
        String name = remoteClient.fetchName(userId);

        // 第一条sql语句
        return repository.save(new Customer(null, name));
    }
}
