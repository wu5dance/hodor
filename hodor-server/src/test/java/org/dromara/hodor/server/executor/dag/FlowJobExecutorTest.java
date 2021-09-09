package org.dromara.hodor.server.executor.dag;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hodor.common.dag.Dag;
import org.dromara.hodor.common.dag.Node;
import org.dromara.hodor.common.dag.NodeLayer;
import org.dromara.hodor.common.dag.Status;
import org.dromara.hodor.common.event.AbstractAsyncEventPublisher;
import org.dromara.hodor.common.event.Event;
import org.dromara.hodor.common.utils.ThreadUtils;
import org.dromara.hodor.core.dag.DagCreator;
import org.dromara.hodor.core.dag.NodeBean;
import org.dromara.hodor.model.job.JobDesc;
import org.dromara.hodor.remoting.api.message.RemotingResponse;
import org.dromara.hodor.remoting.api.message.response.JobExecuteResponse;
import org.dromara.hodor.scheduler.api.HodorJobExecutionContext;
import org.dromara.hodor.server.executor.JobDispatcher;
import org.dromara.hodor.server.executor.handler.RequestHandler;
import org.junit.Test;

/**
 * FlowJobExecutorTest
 *
 * @author tomgs
 * @since 2021/9/6
 */
@Slf4j
public class FlowJobExecutorTest extends AbstractAsyncEventPublisher<Node> {

    private final ExecutorService executorService = Executors.newFixedThreadPool(8);

    private final JobDispatcher jobDispatcher = new JobDispatcher(new FlowJobRequestHandler());

    private final CountDownLatch aliveLatch = new CountDownLatch(1);

    private Dag dag;

    @Test
    public void executeFlow() throws InterruptedException {
        NodeBean flowNode = createFlowNode3();
        final Dag dag = createDag(flowNode);
        Map<Integer, List<Node>> collect = dag.getLayerNodeMap();
        for (Map.Entry<Integer, List<Node>> nodeEntry : collect.entrySet()) {
            System.out.println("------------------ execute layer " + nodeEntry.getKey());
            CountDownLatch countDownLatch = new CountDownLatch(nodeEntry.getValue().size());
            nodeEntry.getValue().forEach(n -> executorService.execute(() -> {
                System.out.println(Thread.currentThread().getName() + "执行：" + n);
                countDownLatch.countDown();
                n.setStatus(Status.SUCCESS);
            }));
            countDownLatch.await();
        }

    }

    @Test
    public void executeSuccessFlow() {
        try {
            NodeBean flowNode = createFlowNode3();
            this.dag = createDag(flowNode);
            Optional<NodeLayer> firstLayer = dag.getFirstLayer();
            dag.setStatus(Status.RUNNING);
            firstLayer.ifPresent(this::submitLayerNode);
            aliveLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            aliveLatch.countDown();
        }
    }

    public void submitLayerNode(NodeLayer nodeLayer) {
        log.info("=============== 开始执行第{}层 =============", nodeLayer.getLayer());
        List<Node> nodes = nodeLayer.getNodes();
        nodeLayer.setStatus(Status.RUNNING);
        nodeLayer.setRunningNodes(nodes.size());
        for (Node node : nodes) {
            publish(Event.create(node, Status.RUNNING));
        }
    }

    @Override
    public void registerListener() {
        this.addListener(event -> {
            Node node = event.getValue();
            if (!node.isReady()) {
                log.error("node {} status {} is not ready.", node.getNodeKeyName(), node.getStatus());
                return;
            }
            node.changeStatus(Status.RUNNING);

            log.info("{} is RUNNING", node);

            JobDesc jobDesc = (JobDesc) node.getRawData();
            //JobDesc jobDesc = new JobDesc();
            jobDesc.setGroupName("testGroup");
            jobDesc.setJobName(node.getNodeName());
            HodorJobExecutionContext hodorJobExecutionContext = new HodorJobExecutionContext(node.getNodeId(), null, jobDesc, "test-scheduler", new Date());
            jobDispatcher.dispatch(hodorJobExecutionContext);
        }, Status.RUNNING);

        this.addListener(event -> {
            Node node = event.getValue();
            node.markSuccess();

            log.info("{} is SUCCESS", node);

            NodeLayer nodeLayer = node.getNodeLayer();
            if (!nodeLayer.getStatus().isTerminal()) {
                return;
            }
            int layer = node.getLayer();
            log.info("=============== 第{}层执行成功 =============", layer);
            // all layer success
            if (dag.isLastLayer(layer)) {
                dag.setStatus(Status.SUCCESS);
                log.info("=============== DAG {} 执行成功 =============", dag);
                aliveLatch.countDown();
            } else {
                // submit next layer node
                submitLayerNode(dag.getLayer(layer + 1));
            }
            // persist dag instance
        }, Status.SUCCESS);

        this.addListener(event -> {
            Node node = event.getValue();
            node.markFailed();
            log.info("{} is FAILURE", node);
        }, Status.FAILURE);

        this.addListener(event -> {
            Node node = event.getValue();
            node.markKilled();
            node.setStatus(Status.KILLED);
            Dag dag = node.getDag();
            NodeLayer nodeLayer = node.getCurrentNodeLayer();
            if (nodeLayer.getRunningNodes() == 0) {
                log.info("{} is KILLED", node);
                dag.setStatus(Status.KILLED);
            }
        }, Status.KILLED);
    }

    private Dag createDag(final NodeBean flowNode) {
        final DagCreator creator = new DagCreator(flowNode);
        return creator.create();
    }

    private NodeBean createFlowNode3() {
        String groupName = "testGroup";
        NodeBean rootNode = buildSubFlowNode(groupName, "root");
        NodeBean rn1 = buildSubFlowNode(groupName,"n1", "root");
        NodeBean rn2 = buildSubFlowNode(groupName,"r-n2", "root");

        NodeBean n2 = buildSubFlowNode(groupName,"n2", "n1");
        NodeBean n3 = buildSubFlowNode(groupName,"n3", "n1");
        NodeBean n4 = buildSubFlowNode(groupName,"n4", "n1");

        NodeBean n5 = buildSubFlowNode(groupName,"n5", "n2");
        NodeBean n6 = buildSubFlowNode(groupName,"n6", "n2");
        NodeBean n7 = buildSubFlowNode(groupName,"n7", "n2");

        NodeBean n8 = buildSubFlowNode(groupName,"n8", "n4");
        NodeBean n9 = buildSubFlowNode(groupName, "n9", "n4");
        NodeBean n10 = buildSubFlowNode(groupName, "n10", "n4");

        NodeBean n11 = buildSubFlowNode(groupName,"n11", "n5", "n6", "n7");
        NodeBean n12 = buildSubFlowNode(groupName,"n12", "n3");
        NodeBean n13 = buildSubFlowNode(groupName,"n13", "n8", "n9", "n10");

        NodeBean n14 = buildSubFlowNode("n14", "n11", "n12", "n13");

        NodeBean flowNode = buildSubFlowNode(groupName, "flow");
        flowNode.setNodes(ImmutableList.of(rootNode, rn1, rn2, n4, n7, n5, n6, n2, n3, n8, n9, n11, n13, n14, n12, n10));

        return flowNode;
    }

    private NodeBean buildSubFlowNode(String groupName, String name, String... depends) {
        NodeBean node = new NodeBean();
        node.setGroupName(groupName);
        node.setNodeName(name);
        node.setDependsOn(Lists.newArrayList(depends));
        return node;
    }

    class FlowJobRequestHandler implements RequestHandler {

        Random random = new Random();

        @Override
        public void preHandle(HodorJobExecutionContext context) {
            log.info("preHandler {} {}", context.getRequestId(), context.getJobKey());
        }

        @Override
        public void handle(HodorJobExecutionContext context) {
            log.info("handle {} {}", context.getRequestId(), context.getJobKey());
            ThreadUtils.sleep(TimeUnit.SECONDS, random.nextInt(5));
            Node node = dag.getNode(context.getJobKey().getGroupName(), context.getJobKey().getJobName());
            publish(Event.create(node, Status.SUCCESS));
        }

        @Override
        public void postHandle(HodorJobExecutionContext context) {
            log.info("postHandle {} {}", context.getRequestId(), context.getJobKey());
        }

        @Override
        public void resultHandle(Map<String, Object> attachment, RemotingResponse<JobExecuteResponse> remotingResponse) {

        }

        @Override
        public void exceptionCaught(HodorJobExecutionContext context, Throwable t) {
            log.info("exceptionCaught {} {}", context.getRequestId(), context.getJobKey());
            Node node = dag.getNode(context.getJobKey().getGroupName(), context.getJobKey().getJobName());
            publish(Event.create(node, Status.FAILURE));
        }
    }
}
