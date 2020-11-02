kubectl create -f /root/livefeed/manifest/livefeed-1-pricing-audusd.yaml
kubectl create -f /root/livefeed/manifest/livefeed-1-pricing-nzdusd.yaml
kubectl create -f /root/livefeed/manifest/livefeed-1-pricing-eurjpy.yaml
kubectl create -f /root/livefeed/manifest/livefeed-1-pricing-eurusd.yaml
kubectl create -f /root/livefeed/manifest/livefeed-1-pricing-gbpchf.yaml
kubectl create -f /root/livefeed/manifest/livefeed-1-pricing-gbpusd.yaml
kubectl create -f /root/livefeed/manifest/livefeed-1-pricing-usdchf.yaml
kubectl create -f /root/livefeed/manifest/livefeed-1-pricing-usdcad.yaml
kubectl create -f /root/livefeed/manifest/livefeed-1-pricing-usdjpy.yaml
kubectl create -f /root/livefeed/manifest/livefeed-1-signal-foresignal.yaml
kubectl create -f /root/livefeed/manifest/livefeed-1-signal-oanda.yaml
date
echo "start all pricing feeds done"
