package com.store.repository;

import com.store.model.StockLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockLedgerRepository extends JpaRepository<StockLedger, Long> {

}
