package com.skillpath.planner.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure preservation and time accounting for a same-day adaptive revision. */
public final class AdaptiveBudgetPolicyV1 {
    public Result evaluate(int budgetMinutes,List<Task> tasks) {
        if(budgetMinutes<1||budgetMinutes>180||tasks==null||tasks.size()>200)
            throw new IllegalArgumentException("Adaptive budget input is out of bounds");
        Set<Long> ids=new HashSet<>();
        int completed=0,reserved=0;
        for(Task task:tasks){
            if(task==null||task.id()<1||!ids.add(task.id())||task.estimatedMinutes()<1
                    ||task.estimatedMinutes()>360)
                throw new IllegalArgumentException("Invalid or duplicate task");
            switch(task.status()){
                case "COMPLETED" -> {
                    if(task.actualMinutes()==null||task.actualMinutes()<0||task.actualMinutes()>360)
                        throw new IllegalArgumentException("Completed task must have bounded actual minutes");
                    completed+=task.actualMinutes();
                }
                case "IN_PROGRESS","BLOCKED" -> reserved+=task.estimatedMinutes();
                case "ASSIGNED","EXPIRED","SKIPPED","ABANDONED" -> { }
                default -> throw new IllegalArgumentException("Unknown task state");
            }
        }
        int committed=completed+reserved;
        return new Result(completed,reserved,Math.max(0,budgetMinutes-committed),
                Math.max(0,committed-budgetMinutes),
                tasks.stream().filter(task->Set.of("COMPLETED","IN_PROGRESS","BLOCKED")
                        .contains(task.status())).map(Task::id).toList(),
                tasks.stream().filter(task->"ASSIGNED".equals(task.status()))
                        .map(Task::id).toList());
    }

    public record Task(long id,String status,int estimatedMinutes,Integer actualMinutes) {}
    public record Result(int completedActualMinutes,int inProgressReservedMinutes,
                         int remainingMinutes,int overBudgetMinutes,List<Long> preservedTaskIds,
                         List<Long> expirableTaskIds) {}
}
