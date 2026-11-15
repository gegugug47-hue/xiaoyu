package com.xiaoyu.app.di

import com.xiaoyu.app.data.brain.BrainGateway
import com.xiaoyu.app.data.brain.BrainRouter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 数据层的大脑端口绑定到 :brain 模块的路由实现。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BrainModule {
    @Binds
    @Singleton
    abstract fun bindBrainGateway(impl: BrainRouter): BrainGateway
}
