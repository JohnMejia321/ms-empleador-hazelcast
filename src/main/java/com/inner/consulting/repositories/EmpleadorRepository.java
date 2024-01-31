package com.inner.consulting.repositories;

import java.util.UUID;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;
import com.inner.consulting.entities.Empleador;

@Repository
public interface EmpleadorRepository extends CassandraRepository<Empleador, UUID> {

}
