package com.dtao.seminarbooking.repo;

import com.dtao.seminarbooking.model.OtpToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OtpTokenRepository extends MongoRepository<OtpToken, String> {
    // return list (there may be multiple un-used tokens); service will pick latest
    List<OtpToken> findByUserIdAndUsedIsFalse(String userId);
}
