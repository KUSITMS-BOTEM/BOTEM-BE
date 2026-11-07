package com.botem.domain.product.repository

import com.botem.domain.product.entity.ProductRequest
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRequestRepository : JpaRepository<ProductRequest, Long>
