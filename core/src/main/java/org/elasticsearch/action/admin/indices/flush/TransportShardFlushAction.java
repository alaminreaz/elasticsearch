/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.action.admin.indices.flush;

import org.elasticsearch.action.ActionWriteResponse;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.replication.TransportReplicationAction;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.action.index.MappingUpdatedAction;
import org.elasticsearch.cluster.action.shard.ShardStateAction;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

/**
 *
 */
public class TransportShardFlushAction extends TransportReplicationAction<ShardFlushRequest, ShardFlushRequest, ActionWriteResponse> {

    public static final String NAME = FlushAction.NAME + "[s]";

    @Inject
    public TransportShardFlushAction(Settings settings, TransportService transportService, ClusterService clusterService,
                                     IndicesService indicesService, ThreadPool threadPool, ShardStateAction shardStateAction,
                                     MappingUpdatedAction mappingUpdatedAction, ActionFilters actionFilters,
                                     IndexNameExpressionResolver indexNameExpressionResolver) {
        super(settings, NAME, transportService, clusterService, indicesService, threadPool, shardStateAction, mappingUpdatedAction,
                actionFilters, indexNameExpressionResolver, ShardFlushRequest::new, ShardFlushRequest::new, ThreadPool.Names.FLUSH);
    }

    @Override
    protected ActionWriteResponse newResponseInstance() {
        return new ActionWriteResponse();
    }

    @Override
    protected Tuple<ActionWriteResponse, ShardFlushRequest> shardOperationOnPrimary(ClusterState clusterState, ShardFlushRequest shardRequest) throws Throwable {
        IndexShard indexShard = indicesService.indexServiceSafe(shardRequest.shardId().getIndex()).getShard(shardRequest.shardId().id());
        indexShard.flush(shardRequest.getRequest());
        logger.trace("{} flush request executed on primary", indexShard.shardId());
        return new Tuple<>(new ActionWriteResponse(), shardRequest);
    }

    @Override
    protected void shardOperationOnReplica(ShardId shardId, ShardFlushRequest request) {
        IndexShard indexShard = indicesService.indexServiceSafe(request.shardId().getIndex()).getShard(request.shardId().id());
        indexShard.flush(request.getRequest());
        logger.trace("{} flush request executed on replica", indexShard.shardId());
    }

    @Override
    protected boolean checkWriteConsistency() {
        return false;
    }

    @Override
    protected ShardId shardId(ClusterState clusterState, InternalRequest internalRequest) {
        return internalRequest.request.shardId();
    }

    @Override
    protected ClusterBlockException checkGlobalBlock(ClusterState state) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    @Override
    protected ClusterBlockException checkRequestBlock(ClusterState state, InternalRequest internalRequest) {
        return state.blocks().indicesBlockedException(ClusterBlockLevel.METADATA_WRITE, new String[]{internalRequest.concreteIndex});
    }

    @Override
    protected boolean shouldExecuteReplication(Settings settings) {
        return true;
    }
}
