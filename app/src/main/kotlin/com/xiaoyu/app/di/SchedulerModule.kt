package com.xiaoyu.app.di

import com.xiaoyu.app.data.scheduler.ExtractionScheduler
import com.xiaoyu.app.work.WorkScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 数据层的调度端口绑定到 app 层的 WorkManager 实现。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SchedulerModule {
    @Binds
    abstract fun bindExtractionScheduler(impl: WorkScheduler): ExtractionScheduler
}
