# vmstat, linux vm statistics

sampleIntervalSeconds=3
numberOfSamples=6

vmstat --wide --one-header $sampleIntervalSeconds $numberOfSamples