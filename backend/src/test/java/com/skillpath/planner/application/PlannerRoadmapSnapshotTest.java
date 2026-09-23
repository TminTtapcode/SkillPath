package com.skillpath.planner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillpath.goal.application.GoalQueries;
import com.skillpath.knowledge.application.PlannerKnowledgeQueries;
import com.skillpath.learning.application.LearningQueries;
import com.skillpath.progress.application.PlannerProgressQueries;
import com.skillpath.review.application.PlannerReviewQueries;
import com.skillpath.shared.api.ApiException;
import com.skillpath.shared.localization.SupportedLocale;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlannerRoadmapSnapshotTest {
    private static final Instant NOW=Instant.parse("2026-09-23T08:00:00Z");
    private static final long USER=10;
    private final GoalQueries goals=mock(GoalQueries.class);
    private final PlannerKnowledgeQueries knowledge=mock(PlannerKnowledgeQueries.class);
    private final PlannerProgressQueries progress=mock(PlannerProgressQueries.class);
    private final PlannerReviewQueries review=mock(PlannerReviewQueries.class);
    private final LearningQueries learning=mock(LearningQueries.class);
    private final PlannerStore store=mock(PlannerStore.class);
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private final PlannerService service=new PlannerService(goals,knowledge,progress,review,learning,
            store,json,Clock.fixed(NOW,ZoneOffset.UTC));

    @Test void fiftyNodeFixturePagesInStableTopologyWithoutMixingSnapshots(){
        configureGraph(50);
        var first=service.roadmap(USER,25,null,SupportedLocale.ENGLISH);
        var second=service.roadmap(USER,25,first.nextCursor(),SupportedLocale.ENGLISH);
        assertThat(first.nodes()).hasSize(25);
        assertThat(second.nodes()).hasSize(25);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.hasMore()).isFalse();
        assertThat(second.nextCursor()).isNull();
        assertThat(first.projectionAsOf()).isEqualTo(second.projectionAsOf()).isEqualTo(NOW);
        assertThat(first.progressDigest()).isEqualTo(second.progressDigest());
        assertThat(first.reviewDigest()).isEqualTo(second.reviewDigest());
        Set<String> ids=new HashSet<>();
        first.nodes().forEach(node->assertThat(ids.add(node.id())).isTrue());
        second.nodes().forEach(node->assertThat(ids.add(node.id())).isTrue());
        assertThat(ids).hasSize(50);
        assertThat(first.edges()).hasSize(24);
        assertThat(second.edges()).hasSize(25);
        assertThat(second.edges().getFirst().sourceId()).isEqualTo("25");
        assertThat(second.edges().getFirst().targetId()).isEqualTo("26");
        assertThat(second.nodes().getFirst().blockedBy()).containsExactly("25");
        verify(progress,times(2)).snapshot(eq(USER),eq(7L),any(),eq(NOW));
        verify(review,times(2)).snapshot(eq(USER),eq(7L),any(),eq(NOW));
    }

    @Test void twoHundredNodeBoundPaginatesWithoutLosingCrossPageEdges(){
        configureGraph(200);
        String cursor=null;
        Set<String> ids=new HashSet<>();
        int edgeCount=0;
        for(int page=0;page<4;page++){
            var result=service.roadmap(USER,50,cursor,SupportedLocale.ENGLISH);
            assertThat(result.nodes()).hasSize(50);
            result.nodes().forEach(node->assertThat(ids.add(node.id())).isTrue());
            edgeCount+=result.edges().size();
            assertThat(result.hasMore()).isEqualTo(page<3);
            cursor=result.nextCursor();
        }
        assertThat(cursor).isNull();
        assertThat(ids).hasSize(200);
        assertThat(edgeCount).isEqualTo(199);
    }

    @Test void incompatibleStateFailsClosedBeforeAnyPlanWrite(){
        configureGraph(2);
        when(progress.snapshot(eq(USER),eq(7L),any(),eq(NOW)))
                .thenReturn(new PlannerProgressQueries.Snapshot("knowledge-state-v1","changed",false,List.of()));
        assertThatThrownBy(()->service.generate(USER,"incompatible",SupportedLocale.ENGLISH))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not compatible");
        verify(store,never()).snapshot(anyLong(),anyLong(),anyLong(),
                any(),any(),any(),any(),any(),anyInt(),anyInt());
    }

    @Test void pinnedPlanShowsStaleInsteadOfRelabelingOldWorkCurrent()throws Exception{
        configureGraph(2);
        var pinned=new PlannerService.SnapshotInput(NOW,knowledge.planningGraph(1),
                new PlannerProgressQueries.Snapshot("knowledge-state-v1","progress-7",true,List.of()),
                new PlannerReviewQueries.Snapshot("review-7",true,List.of()),List.of());
        var row=new PlannerStore.PlanRow(77,USER,5,LocalDate.parse("2026-09-23"),
                "Asia/Ho_Chi_Minh",60,1,88L,"CURRENT","PLANNED",7,NOW,
                "progress-7","review-7",json.writeValueAsString(pinned));
        when(store.current(eq(USER),eq(5L),any())).thenReturn(Optional.of(row));
        when(store.items(77)).thenReturn(List.of(new PlannerStore.ItemRow(1,99,100,1,500,10,
                new BigDecimal("70.00"),"[]")));
        when(progress.snapshot(eq(USER),eq(7L),any(),eq(NOW)))
                .thenReturn(new PlannerProgressQueries.Snapshot("knowledge-state-v1",
                        "progress-changed",true,List.of()));
        var map=service.roadmap(USER,50,null,SupportedLocale.ENGLISH);
        assertThat(map.stale()).isTrue();
        assertThat(map.planId()).isEqualTo("77");
        assertThat(map.progressDigest()).isEqualTo("progress-7");
        assertThat(map.nodes().getFirst().current()).isFalse();
        assertThat(map.projectionAsOf()).isEqualTo(NOW);
    }

    private void configureGraph(int count){
        when(goals.planningGoalForUser(eq(USER),anyBoolean()))
                .thenReturn(new GoalQueries.PlanningGoal(5,1,LocalDate.parse("2027-01-01"),
                        "Asia/Ho_Chi_Minh",60,"ACTIVE"));
        when(store.current(eq(USER),eq(5L),any())).thenReturn(Optional.empty());
        List<PlannerKnowledgeQueries.Node> nodes=new ArrayList<>();
        List<PlannerKnowledgeQueries.Edge> edges=new ArrayList<>();
        for(int i=1;i<=count;i++){
            nodes.add(new PlannerKnowledgeQueries.Node(i,"concept-"+i,"Concept "+i,
                    "Khái niệm "+i,"ACTIVE",new BigDecimal("0.8"),
                    new BigDecimal("0.8"),i==count,i-1));
            if(i>1)edges.add(new PlannerKnowledgeQueries.Edge(i-1,i,"PREREQUISITE",
                    new BigDecimal("1.0")));
        }
        when(knowledge.planningGraph(1)).thenReturn(new PlannerKnowledgeQueries.PlanningGraph(
                7,nodes,edges));
        when(progress.snapshot(eq(USER),eq(7L),any(),eq(NOW)))
                .thenReturn(new PlannerProgressQueries.Snapshot("knowledge-state-v1","progress-7",
                        true,List.of()));
        when(review.snapshot(eq(USER),eq(7L),any(),eq(NOW)))
                .thenReturn(new PlannerReviewQueries.Snapshot("review-7",true,List.of()));
        when(learning.activeVariants(7)).thenReturn(List.of());
    }
}
