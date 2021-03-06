/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kafka.streams.kstream.internals;

import org.apache.kafka.streams.errors.StreamsException;
import org.apache.kafka.streams.kstream.ValueJoiner;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;

class KTableKTableOuterJoin<K, R, V1, V2> extends KTableKTableAbstractJoin<K, R, V1, V2> {

    KTableKTableOuterJoin(KTableImpl<K, ?, V1> table1, KTableImpl<K, ?, V2> table2, ValueJoiner<V1, V2, R> joiner) {
        super(table1, table2, joiner);
    }

    @Override
    public Processor<K, Change<V1>> get() {
        return new KTableKTableOuterJoinProcessor(valueGetterSupplier2.get());
    }

    @Override
    public KTableValueGetterSupplier<K, R> view() {
        return new KTableValueGetterSupplier<K, R>() {

            public KTableValueGetter<K, R> get() {
                return new KTableKTableOuterJoinValueGetter(valueGetterSupplier1.get(), valueGetterSupplier2.get());
            }

        };
    }

    private class KTableKTableOuterJoinProcessor extends AbstractProcessor<K, Change<V1>> {

        private final KTableValueGetter<K, V2> valueGetter;

        public KTableKTableOuterJoinProcessor(KTableValueGetter<K, V2> valueGetter) {
            this.valueGetter = valueGetter;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void init(ProcessorContext context) {
            super.init(context);
            valueGetter.init(context);
        }

        /**
         * @throws StreamsException if key is null
         */
        @Override
        public void process(K key, Change<V1> change) {
            // the keys should never be null
            if (key == null)
                throw new StreamsException("Record key for KTable outer-join operator should not be null.");

            R newValue = null;
            R oldValue = null;
            V2 value2 = valueGetter.get(key);

            if (change.newValue != null || value2 != null)
                newValue = joiner.apply(change.newValue, value2);

            if (sendOldValues) {
                if (change.oldValue != null || value2 != null)
                    oldValue = joiner.apply(change.oldValue, value2);
            }

            context().forward(key, new Change<>(newValue, oldValue));
        }
    }

    private class KTableKTableOuterJoinValueGetter implements KTableValueGetter<K, R> {

        private final KTableValueGetter<K, V1> valueGetter1;
        private final KTableValueGetter<K, V2> valueGetter2;

        public KTableKTableOuterJoinValueGetter(KTableValueGetter<K, V1> valueGetter1, KTableValueGetter<K, V2> valueGetter2) {
            this.valueGetter1 = valueGetter1;
            this.valueGetter2 = valueGetter2;
        }

        @Override
        public void init(ProcessorContext context) {
            valueGetter1.init(context);
            valueGetter2.init(context);
        }

        @Override
        public R get(K key) {
            R newValue = null;
            V1 value1 = valueGetter1.get(key);
            V2 value2 = valueGetter2.get(key);

            if (value1 != null || value2 != null)
                newValue = joiner.apply(value1, value2);

            return newValue;
        }

    }

}
