/*
 * Copyright (c) 2014 Hewlett-Packard Development Company, L.P.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hpcloud.mon.persister.repository;

import com.codahale.metrics.Timer;
import com.hpcloud.mon.common.event.AlarmStateTransitionedEvent;
import com.hpcloud.mon.persister.configuration.MonPersisterConfiguration;
import io.dropwizard.setup.Environment;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.PreparedBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class VerticaAlarmStateHistoryRepository extends VerticaRepository {

    private static final Logger logger = LoggerFactory.getLogger(VerticaAlarmStateHistoryRepository.class);
    private final MonPersisterConfiguration configuration;
    private final Environment environment;

    private static final String SQL_INSERT_INTO_ALARM_HISTORY =
            "insert into MonAlarms.StateHistory (tenant_id, alarm_id, old_state, new_state, reason, reason_data, time_stamp) values (:tenant_id, :alarm_id, :old_state, :new_state, :reason, :reason_data, :time_stamp)";
    private PreparedBatch batch;
    private final Timer commitTimer;
    private final SimpleDateFormat simpleDateFormat;

    @Inject
    public VerticaAlarmStateHistoryRepository(DBI dbi, MonPersisterConfiguration configuration,
                                              Environment environment) throws NoSuchAlgorithmException, SQLException {
        super(dbi);
        logger.debug("Instantiating: " + this);

        this.configuration = configuration;
        this.environment = environment;
        this.commitTimer = this.environment.metrics().timer(this.getClass().getName() + "." + "commits-timer");

        handle.getConnection().setAutoCommit(false);
        batch = handle.prepareBatch(SQL_INSERT_INTO_ALARM_HISTORY);
        handle.begin();

        simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT-0"));
    }

    public void addToBatch(AlarmStateTransitionedEvent message) {
        String timeStamp = simpleDateFormat.format(new Date(message.timestamp * 1000));
        batch.add()
                .bind(0, message.tenantId)
                .bind(1, message.alarmId)
                .bind(2, message.oldState.name())
                .bind(3, message.newState.name())
                .bind(4, message.stateChangeReason)
                .bind(5, "{}")
                .bind(6, timeStamp);
    }

    public void flush() {
        commitBatch();
    }

    private void commitBatch() {
        long startTime = System.currentTimeMillis();
        Timer.Context context = commitTimer.time();
        batch.execute();
        handle.commit();
        handle.begin();
        context.stop();
        long endTime = System.currentTimeMillis();
        logger.debug("Commiting batch took " + (endTime - startTime) / 1000 + " seconds");
    }
}