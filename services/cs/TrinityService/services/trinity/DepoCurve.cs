﻿using System;
using System.Collections.Generic;
using System.Linq;
using MarketData2;

namespace com.trafigura.services.trinity
{
    public class DepoCurve : Curve<DepoRate>
    {
        public DepoCurve(trMarketDataServer marketData, Profile profile, string commodity)
            : base (marketData, profile, commodity)
        {
        }

        protected override trRateCurve TrinityRateCurve
        {
            get { return marketData.RateCurve[Profile.Id, Commodity, null, trRateCurveTypeEnum.trRateCurveDepo]; }
        }

        protected override trRateCurve GetOrCreateTrinityRateCurve()
        {
            return TrinityRateCurve ?? marketData.NewRateCurve[Profile.Id, Commodity, null, trRateCurveTypeEnum.trRateCurveDepo];
        }

        public override List<DepoRate> Rates
        {
            get { return TrinityRateCurve.Rates().Select(Create).ToList(); }
        }

        private static DepoRate Create(_IRate rate)
        {
            var depoRate = (trDepoRate) rate;

            return new DepoRate
            {
                Period = depoRate.Period,
                PeriodFromToday = depoRate.PeriodFromToday,
                Bid = depoRate.Bid,
                Offer = depoRate.Off,
                Date = DateTime.FromOADate(depoRate.TheDate).ToShortDateString()
            };
        }
    }
}