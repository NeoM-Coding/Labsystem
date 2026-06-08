/*
 * Copyright (c) 2017 Baidu, Inc. All Rights Reserve.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.sunjieyi60.uid.starter.buffer;

/**
 * If cursor catches the tail it means that the ring buffer is empty, any more buffer take request will be rejected.
 * Specify the policy to handle the reject. This is a Lambda supported interface
 * 拒绝策略: 当环已空, 无法继续获取时
 * 默认无需指定, 将记录日志, 并抛出UidGenerateException异常. 如有特殊需求, 请实现RejectedTakeBufferHandler接口(支持Lambda表达式)
 *
 * @author yutianbao
 */
@FunctionalInterface
public interface RejectedTakeBufferHandler {

    /**
     * Reject take buffer request
     *
     * @param ringBuffer
     */
    void rejectTakeBuffer(RingBuffer ringBuffer);
}
